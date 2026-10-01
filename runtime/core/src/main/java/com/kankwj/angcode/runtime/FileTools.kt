package com.kankwj.angcode.runtime

import java.io.File

class WorkspaceListTool : AgentTool {
    override val id = "workspace.list"
    override val description = "Lista archivos y carpetas dentro del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"].orEmpty()
        val target = safePath(context.workspace, path)
        if (!target.exists()) return ToolResponse(false, "La ruta no existe: $path")
        if (!target.isDirectory) return ToolResponse(false, "La ruta no es una carpeta: $path")

        val output = target.listFiles()
            .orEmpty()
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .joinToString("\n") { file ->
                val kind = if (file.isDirectory) "dir" else "file"
                "$kind\t${file.name}\t${if (file.isFile) file.length() else 0}"
            }
        return ToolResponse(true, output)
    }
}

class FileReadTool : AgentTool {
    override val id = "file.read"
    override val description = "Lee un archivo de texto dentro del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val target = safePath(context.workspace, path)
        if (!target.isFile) return ToolResponse(false, "Archivo no encontrado: $path")

        val maxBytes = call.arguments["maxBytes"]?.toIntOrNull()?.coerceIn(1, 1_000_000) ?: 200_000
        if (target.length() > maxBytes) {
            return ToolResponse(false, "Archivo demasiado grande para file.read (${target.length()} bytes)")
        }
        return ToolResponse(true, target.readText())
    }
}

class FileWriteTool : AgentTool {
    override val id = "file.write"
    override val description = "Crea o reemplaza un archivo de texto dentro del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val content = call.arguments["content"] ?: return ToolResponse(false, "Falta content")
        val target = safePath(context.workspace, path)
        target.parentFile?.mkdirs()
        target.writeText(content)
        return ToolResponse(true, "Escrito: $path", mapOf("bytes" to target.length().toString()))
    }
}

class FileDeleteTool : AgentTool {
    override val id = "file.delete"
    override val description = "Elimina una ruta del workspace; para carpetas no vacías requiere recursive=true."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        if (path.isBlank()) return ToolResponse(false, "No se puede eliminar la raíz del workspace")
        val target = safePath(context.workspace, path)
        if (!target.exists()) return ToolResponse(false, "Ruta no encontrada")
        val recursive = call.arguments["recursive"]?.toBooleanStrictOrNull() ?: false
        val ok = if (target.isDirectory && recursive) target.deleteRecursively() else target.delete()
        return if (ok) ToolResponse(true, "Eliminado: $path") else ToolResponse(false, "No se pudo eliminar")
    }
}

fun ToolBroker.registerCoreTools(
    policy: ExecutionPolicy = ExecutionPolicy.androidBase(),
    processRegistry: ProcessRegistry = ProcessRegistry(),
    checkpointManager: CheckpointManager = CheckpointManager()
): ToolBroker = apply {
    register(SystemCommandTool(policy = policy))
    register(ProcessStartTool(processRegistry, policy))
    register(ProcessLogsTool(processRegistry))
    register(ProcessListTool(processRegistry))
    register(ProcessStopTool(processRegistry))

    register(WorkspaceListTool())
    register(WorkspaceSearchTool())
    register(CodeSearchTool())
    register(CodeSymbolsTool())
    register(FileReadTool())
    register(FileWriteTool())
    register(FileDeleteTool())
    register(FilePatchTool())
    register(FileHashTool())

    register(WorkspaceZipTool())
    register(WorkspaceUnzipTool())
    register(ArtifactZipTool())

    register(CheckpointCreateTool(checkpointManager))
    register(CheckpointListTool(checkpointManager))
    register(CheckpointRestoreTool(checkpointManager))

    register(GitStatusTool())
    register(GitDiffTool())
    register(GitLogTool())
    register(GitAddTool())
    register(GitCommitTool())

    register(HttpGetTool())
    register(DeviceInfoTool())
}
