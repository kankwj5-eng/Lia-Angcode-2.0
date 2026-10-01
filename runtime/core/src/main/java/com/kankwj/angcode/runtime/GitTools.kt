package com.kankwj.angcode.runtime

import java.io.File

private fun gitExecutable(context: ToolContext): File? {
    val configured = context.executables["git"] ?: return null
    val file = File(configured)
    return file.takeIf { it.isFile && it.canExecute() }
}

private fun runGit(
    context: ToolContext,
    args: List<String>,
    timeoutMs: Long = 30_000
): ToolResponse {
    val git = gitExecutable(context)
        ?: return ToolResponse(false, "Git no está instalado/registrado en este runtime")

    val result = CommandRunner().run(
        CommandRequest(
            executable = git.absolutePath,
            arguments = args,
            workingDirectory = context.workspace,
            timeoutMillis = timeoutMs
        )
    )
    val output = buildString {
        if (result.stdout.isNotBlank()) append(result.stdout.trimEnd())
        if (result.stderr.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append(result.stderr.trimEnd())
        }
    }
    return ToolResponse(
        result.succeeded,
        output,
        mapOf(
            "exitCode" to result.exitCode.toString(),
            "durationMs" to result.durationMillis.toString()
        )
    )
}

class GitStatusTool : AgentTool {
    override val id = "git.status"
    override val description = "Estado corto y rama del repositorio Git."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)
    override fun invoke(call: ToolCall, context: ToolContext) =
        runGit(context, listOf("status", "--short", "--branch"))
}

class GitDiffTool : AgentTool {
    override val id = "git.diff"
    override val description = "Diff del workspace, opcionalmente para una ruta."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"]
        val args = mutableListOf("diff", "--no-ext-diff", "--")
        if (!path.isNullOrBlank()) {
            safeWorkspaceFile(context.workspace, path)
            args += path
        }
        return runGit(context, args)
    }
}

class GitLogTool : AgentTool {
    override val id = "git.log"
    override val description = "Historial compacto del repositorio."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val count = call.arguments["count"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        return runGit(context, listOf("log", "--oneline", "--decorate", "--max-count=" + count))
    }
}

class GitAddTool : AgentTool {
    override val id = "git.add"
    override val description = "Añade una ruta concreta al staging area."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        safeWorkspaceFile(context.workspace, path)
        return runGit(context, listOf("add", "--", path))
    }
}

class GitCommitTool : AgentTool {
    override val id = "git.commit"
    override val description = "Crea un commit con los cambios ya preparados."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val message = call.arguments["message"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ToolResponse(false, "Falta message")
        if (message.length > 500) return ToolResponse(false, "Mensaje demasiado largo")
        return runGit(context, listOf("commit", "-m", message), 60_000)
    }
}
