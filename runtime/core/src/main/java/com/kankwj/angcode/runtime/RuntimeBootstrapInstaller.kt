package com.kankwj.angcode.runtime

import android.content.Context
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

data class RuntimeLayout(
    val filesDir: File
) {
    val prefix: File = File(filesDir, "usr")
    val stagingPrefix: File = File(filesDir, "usr-staging")
    val backupPrefix: File = File(filesDir, "usr-backup")
    val home: File = File(filesDir, "home")
}

data class RuntimeInstallResult(
    val success: Boolean,
    val sha256: String,
    val filesExtracted: Int,
    val symlinksCreated: Int,
    val prefix: String,
    val detail: String
)

class RuntimeBootstrapInstaller(
    private val context: Context
) {
    private val layout = RuntimeLayout(context.applicationContext.filesDir)

    fun install(
        bootstrap: InputStream,
        expectedSha256: String
    ): RuntimeInstallResult {
        val expected = expectedSha256.trim().lowercase()
        require(expected.matches(Regex("^[0-9a-f]{64}$"))) {
            "SHA-256 esperado inválido"
        }

        val stagedZip = File(
            context.cacheDir,
            "angcode-bootstrap-" + UUID.randomUUID() + ".zip"
        )

        val archiveBudget = ArchiveInstallBudget()
        val actual = stagedZip.outputStream().buffered().use { output ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            bootstrap.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    archiveBudget.onArchiveBytes(read.toLong())
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

        if (actual != expected) {
            stagedZip.delete()
            return RuntimeInstallResult(
                success = false,
                sha256 = actual,
                filesExtracted = 0,
                symlinksCreated = 0,
                prefix = layout.prefix.absolutePath,
                detail = "SHA-256 no coincide"
            )
        }

        return try {
            installVerifiedArchive(stagedZip, actual)
        } finally {
            stagedZip.delete()
        }
    }

    private fun installVerifiedArchive(
        archive: File,
        sha256: String
    ): RuntimeInstallResult {
        deleteSafely(layout.stagingPrefix)
        layout.stagingPrefix.mkdirs()
        layout.home.mkdirs()

        var filesExtracted = 0
        val symlinks = mutableListOf<Pair<String, String>>()
        val seenEntries = HashSet<String>()
        val extractionBudget = ArchiveInstallBudget()

        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                extractionBudget.onEntry()
                require(seenEntries.add(name)) {
                    "Bootstrap rechazado: entrada ZIP duplicada"
                }

                if (name == "SYMLINKS.txt") {
                    val text = readEntryTextLimited(
                        zip,
                        ArchiveInstallBudget.MAX_SYMLINK_TABLE_BYTES
                    )
                    text.lineSequence()
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .forEach { line ->
                            val separator = line.indexOf('←')
                            require(separator > 0 && separator < line.lastIndex) {
                                "Línea de symlink inválida"
                            }
                            val oldPath = line.substring(0, separator)
                            val newPath = line.substring(separator + 1)
                            extractionBudget.onSymlink()
                            symlinks += oldPath to newPath
                        }
                    zip.closeEntry()
                    continue
                }

                val target = safeArchiveTarget(layout.stagingPrefix, name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            extractionBudget.onExtractedBytes(read.toLong())
                            output.write(buffer, 0, read)
                        }
                    }
                    applyExecutablePermissionIfNeeded(name, target)
                    filesExtracted++
                }
                zip.closeEntry()
            }
        }

        require(symlinks.isNotEmpty()) {
            "Bootstrap rechazado: falta SYMLINKS.txt"
        }

        var symlinkCount = 0
        for ((oldPath, relativeNewPath) in symlinks) {
            val newPath = safeArchiveTarget(
                layout.stagingPrefix,
                relativeNewPath.removePrefix("./")
            )
            newPath.parentFile?.mkdirs()

            // File.delete() removes the symlink entry itself and is safe even
            // when the link is dangling. It does not delete the symlink target.
            newPath.delete()
            Os.symlink(oldPath, newPath.absolutePath)
            symlinkCount++
        }

        activateStagingPrefix()

        File(layout.prefix, ".angcode-runtime").writeText(
            buildString {
                appendLine("sha256=" + sha256)
                appendLine("installedAt=" + System.currentTimeMillis())
                appendLine("package=com.kankwj.angcode")
                append("prefix=" + layout.prefix.absolutePath)
            }
        )

        return RuntimeInstallResult(
            success = true,
            sha256 = sha256,
            filesExtracted = filesExtracted,
            symlinksCreated = symlinkCount,
            prefix = layout.prefix.absolutePath,
            detail = "Runtime instalado"
        )
    }

    private fun activateStagingPrefix() {
        deleteSafely(layout.backupPrefix)

        val hadPrevious = layout.prefix.exists()
        if (hadPrevious && !layout.prefix.renameTo(layout.backupPrefix)) {
            error("No se pudo mover el runtime anterior a backup")
        }

        if (!layout.stagingPrefix.renameTo(layout.prefix)) {
            if (hadPrevious) {
                layout.backupPrefix.renameTo(layout.prefix)
            }
            error("No se pudo activar el nuevo runtime")
        }

        deleteSafely(layout.backupPrefix)
    }

    private fun applyExecutablePermissionIfNeeded(
        entryName: String,
        file: File
    ) {
        if (
            entryName.startsWith("bin/") ||
            entryName.startsWith("libexec/") ||
            entryName.startsWith("lib/apt/apt-helper") ||
            entryName.startsWith("lib/apt/methods/")
        ) {
            Os.chmod(file.absolutePath, 0x1C0) // 0700
        }
    }

    private fun safeArchiveTarget(
        root: File,
        entryName: String
    ): File {
        require(entryName.isNotBlank()) { "Entrada ZIP vacía" }
        require(!entryName.startsWith("/")) { "Entrada ZIP absoluta rechazada" }

        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, entryName).canonicalFile

        require(candidate.toPath().startsWith(canonicalRoot.toPath())) {
            "Entrada ZIP intenta salir del prefix"
        }
        return candidate
    }

    private fun readEntryTextLimited(
        input: InputStream,
        maxBytes: Int
    ): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0

        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            require(total <= maxBytes) {
                "Bootstrap rechazado: SYMLINKS.txt demasiado grande"
            }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun deleteSafely(file: File) {
        if (!file.exists()) return
        require(file.canonicalFile.toPath().startsWith(context.filesDir.canonicalFile.toPath())) {
            "Ruta fuera del almacenamiento privado de la app"
        }
        require(file.deleteRecursively()) {
            "No se pudo limpiar " + file.absolutePath
        }
    }
}

data class InstalledRuntimeStatus(
    val installed: Boolean,
    val prefix: String,
    val markerSha256: String?,
    val executableCount: Int,
    val executables: Map<String, String>
)

class InstalledRuntimeInspector(
    private val context: Context
) {
    fun inspect(): InstalledRuntimeStatus {
        val layout = RuntimeLayout(context.applicationContext.filesDir)
        val marker = File(layout.prefix, ".angcode-runtime")
        val sha = marker.takeIf { it.isFile }
            ?.readLines()
            ?.firstOrNull { it.startsWith("sha256=") }
            ?.substringAfter("sha256=")

        val executables = ExecutableDiscovery.forApp(context).asMap()

        return InstalledRuntimeStatus(
            installed = layout.prefix.isDirectory &&
                File(layout.prefix, "bin/bash").isFile,
            prefix = layout.prefix.absolutePath,
            markerSha256 = sha,
            executableCount = executables.size,
            executables = executables
        )
    }
}
