package com.kankwj.angcode.runtime

import java.io.File

private val SANDBOX_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
private val IMAGE_REF = Regex("^[A-Za-z0-9][A-Za-z0-9._/:@-]{0,200}$")
private val GUEST_EXECUTABLE = Regex("^/[A-Za-z0-9_./+:-]{1,240}$")

private fun prootDistroEnvironment(executable: String): Map<String, String> {
    val bin = File(executable).parentFile ?: error("proot-distro sin bin dir")
    val prefix = bin.parentFile ?: error("proot-distro sin prefix")
    val files = prefix.parentFile ?: error("proot-distro sin files dir")
    val home = File(files, "home").apply { mkdirs() }
    val tmp = File(prefix, "tmp").apply { mkdirs() }
    val systemPath = System.getenv("PATH").orEmpty()

    return mapOf(
        "PREFIX" to prefix.absolutePath,
        "HOME" to home.absolutePath,
        "TMPDIR" to tmp.absolutePath,
        "PATH" to (bin.absolutePath + ":" + systemPath),
        "LANG" to "C.UTF-8"
    )
}

private fun runProotDistro(
    context: ToolContext,
    arguments: List<String>,
    timeoutMillis: Long
): ToolResponse {
    val executable = context.executables["proot-distro"]
        ?: return ToolResponse(
            false,
            "proot-distro no está instalado en el runtime",
            mapOf("backend" to "unavailable")
        )

    val result = CommandRunner().run(
        CommandRequest(
            executable = executable,
            arguments = arguments,
            workingDirectory = context.workspace,
            timeoutMillis = timeoutMillis,
            environment = prootDistroEnvironment(executable)
        )
    )

    return ToolResponse(
        result.succeeded,
        buildString {
            if (result.stdout.isNotBlank()) append(result.stdout.trimEnd())
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(result.stderr.trimEnd())
            }
        },
        mapOf(
            "exitCode" to result.exitCode.toString(),
            "durationMs" to result.durationMillis.toString(),
            "backend" to "proot-distro"
        )
    )
}

class SandboxListTool : AgentTool {
    override val id = "sandbox.list"
    override val description = "Lista contenedores Linux instalados por PRoot-Distro."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse =
        runProotDistro(context, listOf("list"), 30_000)
}

class SandboxInstallTool : AgentTool {
    override val id = "sandbox.install"
    override val description = "Instala una imagen OCI/Linux como contenedor PRoot."
    override val requiredPermissions = setOf(
        ToolPermission.PROCESS_EXECUTE,
        ToolPermission.NETWORK,
        ToolPermission.SANDBOX_MANAGE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val image = call.arguments["image"]?.takeIf { IMAGE_REF.matches(it) }
            ?: return ToolResponse(false, "image faltante o inválida")
        val name = call.arguments["name"]?.takeIf { SANDBOX_NAME.matches(it) }

        val args = mutableListOf("install", image)
        if (name != null) {
            args += listOf("--name", name)
        }

        return runProotDistro(context, args, 45 * 60_000L)
    }
}

class SandboxExecTool : AgentTool {
    override val id = "sandbox.exec"
    override val description = "Ejecuta un binario sin shell dentro de un contenedor aislado con /workspace enlazado."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.PROCESS_EXECUTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val name = call.arguments["name"]?.takeIf { SANDBOX_NAME.matches(it) }
            ?: return ToolResponse(false, "name faltante o inválido")
        val executable = call.arguments["executable"]?.takeIf { GUEST_EXECUTABLE.matches(it) }
            ?: return ToolResponse(false, "executable debe ser una ruta absoluta válida dentro del guest")
        val args = call.arguments["args"]
            ?.split('\u001F')
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val command = mutableListOf(
            "login",
            "--isolated",
            "--minimal",
            "--bind",
            context.workspace.canonicalPath + ":/workspace",
            name,
            "--",
            executable
        )
        command += args

        val timeout = call.arguments["timeoutMs"]
            ?.toLongOrNull()
            ?.coerceIn(1_000L, 30 * 60_000L)
            ?: 5 * 60_000L

        return runProotDistro(context, command, timeout)
    }
}

class SandboxRemoveTool : AgentTool {
    override val id = "sandbox.remove"
    override val description = "Elimina permanentemente un contenedor PRoot."
    override val requiredPermissions = setOf(
        ToolPermission.PROCESS_EXECUTE,
        ToolPermission.SANDBOX_MANAGE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val name = call.arguments["name"]?.takeIf { SANDBOX_NAME.matches(it) }
            ?: return ToolResponse(false, "name faltante o inválido")
        return runProotDistro(context, listOf("remove", name), 10 * 60_000L)
    }
}
