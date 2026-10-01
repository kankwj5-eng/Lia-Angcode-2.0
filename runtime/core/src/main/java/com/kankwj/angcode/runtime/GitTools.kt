package com.kankwj.angcode.runtime

import java.io.File

private class GitRunner(
    private val executable: File,
    private val runner: CommandRunner = CommandRunner()
) {
    init {
        require(executable.isFile && executable.canExecute()) { "Git no está disponible: " + executable }
    }

    fun run(workspace: File, args: List<String>, timeoutMs: Long = 30_000): ToolResponse {
        val result = runner.run(
            CommandRequest(
                executable = executable.absolutePath,
                arguments = args,
                workingDirectory = workspace,
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
}

class GitStatusTool(private val git: GitRunner) : AgentTool {
    override val id = "git.status"
    override val description = "Estado corto y rama del repositorio Git."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)
    override fun invoke(call: ToolCall, context: ToolContext) =
        git.run(context.workspace, listOf("status", "--short", "--branch"))
}

class GitDiffTool(private val git: GitRunner) : AgentTool {
    override val id = "git.diff"
    override val description = "Diff del workspace, opcionalmente para una ruta."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"]
        val args = mutableListOf("diff", "--no-ext-diff", "--")
        if (!path.isNullOrBlank()) {
            safePath(context.workspace, path)
            args += path
        }
        return git.run(context.workspace, args)
    }
}

class GitLogTool(private val git: GitRunner) : AgentTool {
    override val id = "git.log"
    override val description = "Historial compacto del repositorio."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val count = call.arguments["count"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        return git.run(
            context.workspace,
            listOf("log", "--oneline", "--decorate", "--max-count=" + count)
        )
    }
}

class GitAddTool(private val git: GitRunner) : AgentTool {
    override val id = "git.add"
    override val description = "Añade una ruta concreta al staging area."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        safePath(context.workspace, path)
        return git.run(context.workspace, listOf("add", "--", path))
    }
}

class GitCommitTool(private val git: GitRunner) : AgentTool {
    override val id = "git.commit"
    override val description = "Crea un commit con los cambios ya preparados."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val message = call.arguments["message"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ToolResponse(false, "Falta message")
        if (message.length > 500) return ToolResponse(false, "Mensaje demasiado largo")
        return git.run(context.workspace, listOf("commit", "-m", message), timeoutMs = 60_000)
    }
}

fun ToolBroker.registerGitTools(gitExecutable: File): ToolBroker = apply {
    val git = GitRunner(gitExecutable)
    register(GitStatusTool(git))
    register(GitDiffTool(git))
    register(GitLogTool(git))
    register(GitAddTool(git))
    register(GitCommitTool(git))
}
