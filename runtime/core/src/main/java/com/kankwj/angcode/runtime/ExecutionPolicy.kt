package com.kankwj.angcode.runtime

import java.io.File

data class PolicyDecision(
    val allowed: Boolean,
    val reason: String
)

class ExecutionPolicy private constructor(
    private val exactExecutables: Set<String>,
    private val executableRoots: List<File>
) {
    fun check(executable: String, context: ToolContext, arguments: List<String>): PolicyDecision {
        if (!context.workspace.exists() || !context.workspace.isDirectory) {
            return PolicyDecision(false, "Workspace inválido")
        }

        val target = runCatching { File(executable).canonicalFile }.getOrNull()
            ?: return PolicyDecision(false, "Ruta de ejecutable inválida")

        val exactAllowed = target.path in exactExecutables
        val rootedAllowed = executableRoots.any { root ->
            runCatching { target.toPath().startsWith(root.canonicalFile.toPath()) }.getOrDefault(false)
        }
        if (!exactAllowed && !rootedAllowed) {
            return PolicyDecision(false, "Ejecutable fuera de las raíces permitidas")
        }

        val isShell = target.name in setOf("sh", "bash", "dash", "zsh")
        val evaluatesCommandString = arguments.any { it == "-c" }
        if (isShell && evaluatesCommandString && ToolPermission.UNRESTRICTED_SHELL !in context.grantedPermissions) {
            return PolicyDecision(false, "La shell arbitraria requiere UNRESTRICTED_SHELL")
        }

        return PolicyDecision(true, "permitido")
    }

    companion object {
        fun androidBase(runtimeRoots: List<File> = emptyList()): ExecutionPolicy {
            return ExecutionPolicy(
                exactExecutables = setOf(
                    "/system/bin/sh",
                    "/system/bin/toybox",
                    "/system/bin/logcat"
                ),
                executableRoots = runtimeRoots
            )
        }
    }
}
