package com.kankwj.angcode.runtime

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileHashTool : AgentTool {
    override val id = "file.sha256"
    override val description = "Calcula SHA-256 de un archivo del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val target = safeWorkspaceFile(context.workspace, path)
        if (!target.isFile) return ToolResponse(false, "Archivo no encontrado")

        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(target).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return ToolResponse(true, hex, mapOf("bytes" to target.length().toString()))
    }
}

class ArtifactZipTool : AgentTool {
    override val id = "artifact.zip"
    override val description = "Empaqueta una ruta del workspace en artifacts/*.zip."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val source = safeWorkspaceFile(context.workspace, call.arguments["path"].orEmpty())
        if (!source.exists()) return ToolResponse(false, "Ruta no encontrada")

        val requested = call.arguments["name"]?.trim().orEmpty().ifBlank {
            source.name.ifBlank { "workspace" }
        }
        val safeName = requested.replace(Regex("[^a-zA-Z0-9._-]"), "_").removeSuffix(".zip") + ".zip"
        val artifacts = File(context.workspace, "artifacts").apply { mkdirs() }
        val output = File(artifacts, safeName)

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            val root = if (source.isFile) source.parentFile ?: context.workspace else source
            val sequence = if (source.isFile) sequenceOf(source) else source.walkTopDown().asSequence().filter { it.isFile }
            sequence.forEach { file ->
                if (file.canonicalFile.toPath().startsWith(artifacts.canonicalFile.toPath())) return@forEach
                val relative = file.relativeTo(root).invariantSeparatorsPath
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
