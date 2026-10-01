package com.kankwj.angcode.runtime

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArtifactZipTool : AgentTool {
    override val id = "artifact.zip"
    override val description = "Empaqueta una ruta del workspace en artifacts/*.zip."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val source = safePath(context.workspace, call.arguments["path"].orEmpty())
        if (!source.exists()) return ToolResponse(false, "Ruta no encontrada")

        val requested = call.arguments["name"]?.trim().orEmpty().ifBlank {
            source.name.ifBlank { "workspace" }
        }
        val safeName = requested.replace(Regex("[^a-zA-Z0-9._-]"), "_").removeSuffix(".zip") + ".zip"
        val artifacts = File(context.workspace, "artifacts").apply { mkdirs() }
        val output = safePath(artifacts, safeName)

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
