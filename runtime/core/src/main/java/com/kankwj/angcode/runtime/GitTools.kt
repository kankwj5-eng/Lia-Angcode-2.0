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


object GitWorktreeNaming {
    private val CELL_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
    private val BRANCH = Regex("^[A-Za-z0-9][A-Za-z0-9._/-]{0,120}$")

    fun validCellName(value: String): Boolean = CELL_NAME.matches(value)

    fun validBranch(value: String): Boolean =
        BRANCH.matches(value) &&
            !value.contains("..") &&
            !value.contains("//") &&
            !value.contains("@{") &&
            !value.endsWith("/") &&
            !value.endsWith(".lock")
}

private fun worktreeRoot(context: ToolContext): File {
    val parent = context.workspace.canonicalFile.parentFile
        ?: error("Workspace sin directorio padre")
    val projectKey = context.workspace.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return File(parent, ".angcode-worktrees/" + projectKey).apply { mkdirs() }
}

private fun worktreePath(context: ToolContext, name: String): File {
    require(GitWorktreeNaming.validCellName(name)) { "Nombre de celda inválido" }
    val root = worktreeRoot(context).canonicalFile
    val target = File(root, name).canonicalFile
    require(target.toPath().startsWith(root.toPath())) { "Ruta de worktree inválida" }
    return target
}

class GitWorktreeListTool : AgentTool {
    override val id = "git.worktree.list"
    override val description = "Lista worktrees Git en formato porcelain."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse =
        runGit(context, listOf("worktree", "list", "--porcelain"))
}

class GitWorktreeCreateTool : AgentTool {
    override val id = "git.worktree.create"
    override val description = "Crea un worktree y rama aislados para una celda/agente."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.WORKSPACE_WRITE,
        ToolPermission.WORKTREE_MANAGE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val name = call.arguments["name"]?.takeIf(GitWorktreeNaming::validCellName)
            ?: return ToolResponse(false, "name faltante o inválido")

        val branch = call.arguments["branch"]
            ?: "angcode/" + name
        if (!GitWorktreeNaming.validBranch(branch)) {
            return ToolResponse(false, "branch inválida")
        }

        val base = call.arguments["base"]?.takeIf { it.isNotBlank() } ?: "HEAD"
        if (base.length > 160 || base.any { it.isWhitespace() }) {
            return ToolResponse(false, "base inválida")
        }

        val target = worktreePath(context, name)
        if (target.exists()) {
            return ToolResponse(false, "Ya existe un worktree para esa celda")
        }
        target.parentFile?.mkdirs()

        val result = runGit(
            context,
            listOf("worktree", "add", "-b", branch, target.absolutePath, base),
            2 * 60_000L
        )

        return if (result.ok) {
            result.copy(
                metadata = result.metadata + mapOf(
                    "worktree" to target.absolutePath,
                    "branch" to branch,
                    "cell" to name
                )
            )
        } else {
            target.deleteRecursively()
            result
        }
    }
}

class GitWorktreeRemoveTool : AgentTool {
    override val id = "git.worktree.remove"
    override val description = "Retira un worktree administrado de una celda."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.WORKSPACE_WRITE,
        ToolPermission.WORKTREE_MANAGE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val name = call.arguments["name"]?.takeIf(GitWorktreeNaming::validCellName)
            ?: return ToolResponse(false, "name faltante o inválido")
        val target = worktreePath(context, name)
        val force = call.arguments["force"]?.toBooleanStrictOrNull() ?: false

        val args = mutableListOf("worktree", "remove")
        if (force) args += "--force"
        args += target.absolutePath

        return runGit(context, args, 60_000L)
    }
}
