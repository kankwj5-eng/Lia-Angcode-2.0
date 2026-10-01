package com.kankwj.angcode.runtime

private fun runGit(
    context: ToolContext,
    runner: CommandRunner,
    arguments: List<String>
): ToolResponse {
    val executable = context.executables["git"]
        ?: return ToolResponse(false, "Git aún no está instalado/registrado en este runtime")

    val result = runner.run(
        CommandRequest(
            executable = executable,
            arguments = arguments,
            workingDirectory = context.workspace,
            timeoutMillis = 60_000
        )
    )
    val output = listOf(result.stdout, result.stderr)
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trimEnd()

    return ToolResponse(
        result.succeeded,
        output,
        mapOf("exitCode" to result.exitCode.toString(), "durationMs" to result.durationMillis.toString())
    )
}

class GitStatusTool(private val runner: CommandRunner = CommandRunner()) : AgentTool {
    override val id = "git.status"
    override val description = "Muestra el estado Git del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.PROCESS_EXECUTE)
    override fun invoke(call: ToolCall, context: ToolContext) =
        runGit(context, runner, listOf("status", "--short", "--branch"))
}

class GitDiffTool(private val runner: CommandRunner = CommandRunner()) : AgentTool {
    override val id = "git.diff"
    override val description = "Muestra el diff Git del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val staged = call.arguments["staged"]?.toBooleanStrictOrNull() ?: false
        return runGit(
            context,
            runner,
            if (staged) listOf("diff", "--cached", "--", ".") else listOf("diff", "--", ".")
        )
    }
}

class GitLogTool(private val runner: CommandRunner = CommandRunner()) : AgentTool {
    override val id = "git.log"
    override val description = "Devuelve historial Git compacto."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val count = call.arguments["count"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        return runGit(context, runner, listOf("log", "--oneline", "--decorate", "-n", count.toString()))
    }
}

class GitAddTool(private val runner: CommandRunner = CommandRunner()) : AgentTool {
    override val id = "git.add"
    override val description = "Añade rutas del workspace al staging de Git."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.WORKSPACE_WRITE,
        ToolPermission.PROCESS_EXECUTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val paths = call.arguments["paths"]?.split('\u001F')?.filter { it.isNotBlank() }.orEmpty()
        if (paths.isEmpty()) return ToolResponse(false, "Falta paths")
        paths.forEach { safeWorkspaceFile(context.workspace, it) }
        return runGit(context, runner, listOf("add", "--") + paths)
    }
}

class GitCommitTool(private val runner: CommandRunner = CommandRunner()) : AgentTool {
    override val id = "git.commit"
    override val description = "Crea un commit local con un mensaje explícito."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_WRITE, ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val message = call.arguments["message"]?.trim().orEmpty()
        if (message.isBlank()) return ToolResponse(false, "Falta message")
        return runGit(context, runner, listOf("commit", "-m", message))
    }
}
