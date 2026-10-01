package com.kankwj.angcode.runtime

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class WorkspaceZipTool : AgentTool {
    override val id = "archive.zip"
    override val description = "Comprime una carpeta o archivo del workspace en artifacts."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val sourcePath = call.arguments["path"].orEmpty()
        val outputName = call.arguments["name"]?.takeIf { it.endsWith(".zip", ignoreCase = true) }
            ?: "workspace.zip"

        val source = safePath(context.workspace, sourcePath)
        if (!source.exists()) return ToolResponse(false, "No existe: " + sourcePath)

        val artifacts = File(context.workspace, "artifacts").apply { mkdirs() }
        val output = safePath(artifacts, outputName)

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            val base = if (source.isDirectory) source else source.parentFile ?: context.workspace
            val entries = if (source.isDirectory) source.walkTopDown().asSequence() else sequenceOf(source)

            entries.filter { it.isFile && it.canonicalFile != output.canonicalFile }.forEach { file ->
                val relative = file.relativeTo(base).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }

        return ToolResponse(
            true,
            output.relativeTo(context.workspace).invariantSeparatorsPath,
            mapOf("bytes" to output.length().toString())
        )
    }
}

class WorkspaceUnzipTool : AgentTool {
    override val id = "archive.unzip"
    override val description = "Extrae un ZIP del workspace bloqueando Zip Slip."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val archivePath = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val destinationPath = call.arguments["destination"] ?: "extracted"

        val archive = safePath(context.workspace, archivePath)
        if (!archive.isFile) return ToolResponse(false, "ZIP no encontrado: " + archivePath)

        val destination = safePath(context.workspace, destinationPath).apply { mkdirs() }
        val destinationRoot = destination.canonicalFile
        var extracted = 0

        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(destinationRoot, entry.name).canonicalFile
                if (!target.toPath().startsWith(destinationRoot.toPath())) {
                    return ToolResponse(false, "ZIP rechazado: entrada intenta salir del destino")
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                    extracted++
                }
                zip.closeEntry()
            }
        }

        return ToolResponse(true, "Extraídos " + extracted + " archivos", mapOf("files" to extracted.toString()))
    }
}
