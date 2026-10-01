package com.kankwj.angcode.runtime

import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class Checkpoint(
    val name: String,
    val file: File,
    val createdAt: Long,
    val bytes: Long
)

class CheckpointManager {
    fun create(workspace: File, label: String?): Checkpoint {
        val dir = File(workspace, ".agent/checkpoints").apply { mkdirs() }
        val stamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            .replace(":", "-")
        val safeLabel = label.orEmpty()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(40)
            .trim('_')
        val fileName = if (safeLabel.isBlank()) stamp + ".zip" else stamp + "-" + safeLabel + ".zip"
        val output = File(dir, fileName)

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            workspace.walkTopDown()
                .filter { it.isFile }
                .filterNot { it.toPath().startsWith(dir.toPath()) }
                .forEach { file ->
                    val relative = file.relativeTo(workspace).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }

        return Checkpoint(fileName, output, output.lastModified(), output.length())
    }

    fun list(workspace: File): List<Checkpoint> {
        val dir = File(workspace, ".agent/checkpoints")
        return dir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("zip", true) }
            .sortedByDescending { it.lastModified() }
            .map { Checkpoint(it.name, it, it.lastModified(), it.length()) }
    }

    fun restore(workspace: File, name: String): Int {
        val dir = File(workspace, ".agent/checkpoints").canonicalFile
        val archive = File(dir, name).canonicalFile
        require(archive.toPath().startsWith(dir.toPath()) && archive.isFile) { "Checkpoint inválido" }

        var restored = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(workspace, entry.name).canonicalFile
                require(target.toPath().startsWith(workspace.canonicalFile.toPath())) { "Entrada insegura" }
                require(!target.toPath().startsWith(dir.toPath())) { "El checkpoint no puede sobrescribir checkpoints" }

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                    restored++
                }
                zip.closeEntry()
            }
        }
        return restored
    }
}

class CheckpointCreateTool(private val manager: CheckpointManager) : AgentTool {
    override val id = "checkpoint.create"
    override val description = "Crea una copia comprimida del workspace antes de cambios importantes."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val checkpoint = manager.create(context.workspace, call.arguments["label"])
        return ToolResponse(
            true,
            checkpoint.name,
            mapOf("bytes" to checkpoint.bytes.toString())
        )
    }
}

class CheckpointListTool(private val manager: CheckpointManager) : AgentTool {
    override val id = "checkpoint.list"
    override val description = "Lista checkpoints disponibles."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val list = manager.list(context.workspace)
        return ToolResponse(
            true,
            list.joinToString("\n") { it.name + "\t" + it.bytes },
            mapOf("count" to list.size.toString())
        )
    }
}

class CheckpointRestoreTool(private val manager: CheckpointManager) : AgentTool {
    override val id = "checkpoint.restore"
    override val description = "Restaura archivos desde un checkpoint del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val name = call.arguments["name"] ?: return ToolResponse(false, "Falta name")
        val restored = manager.restore(context.workspace, name)
        return ToolResponse(true, "Restaurados " + restored + " archivos")
    }
}
