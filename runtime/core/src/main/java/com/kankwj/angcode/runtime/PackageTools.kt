package com.kankwj.angcode.runtime

private val PACKAGE_NAME = Regex("^[a-zA-Z0-9][a-zA-Z0-9+._:-]{0,127}$")

class PackageListTool(
    private val runner: CommandRunner = CommandRunner()
) : AgentTool {
    override val id = "package.list"
    override val description = "Lista paquetes instalados cuando dpkg-query está disponible."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val executable = context.executables["dpkg-query"]
            ?: return ToolResponse(
                false,
                "dpkg-query no está disponible en este runtime",
                mapOf("backend" to "unavailable")
            )

        val result = runner.run(
            CommandRequest(
                executable = executable,
                arguments = listOf("-W", "-f=" + 36.toChar() + "{Package}\\t" + 36.toChar() + "{Version}\\n"),
                workingDirectory = context.workspace,
                timeoutMillis = 30_000
            )
        )

        return ToolResponse(
            ok = result.succeeded,
            output = if (result.succeeded) result.stdout.trimEnd() else result.stderr.trimEnd(),
            metadata = mapOf(
                "exitCode" to result.exitCode.toString(),
                "backend" to "dpkg"
            )
        )
    }
}

class PackageInstallTool(
    private val runner: CommandRunner = CommandRunner()
) : AgentTool {
    override val id = "package.install"
    override val description = "Instala paquetes explícitos mediante apt-get/apt en el runtime configurado."
    override val requiredPermissions = setOf(
        ToolPermission.PROCESS_EXECUTE,
        ToolPermission.NETWORK,
        ToolPermission.PACKAGE_MANAGE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val raw = call.arguments["packages"] ?: return ToolResponse(false, "Falta packages")
        val packages = raw
            .split('\u001F', ',', ' ')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

        if (packages.isEmpty()) return ToolResponse(false, "No hay paquetes para instalar")
        if (packages.size > 30) return ToolResponse(false, "Demasiados paquetes en una sola operación")

        val invalid = packages.filterNot { PACKAGE_NAME.matches(it) }
        if (invalid.isNotEmpty()) {
            return ToolResponse(false, "Nombres de paquete inválidos: " + invalid.joinToString())
        }

        val executable = context.executables["apt-get"]
            ?: context.executables["apt"]
            ?: return ToolResponse(
                false,
                "apt/apt-get no está disponible; instala o activa un backend de userland primero",
                mapOf("backend" to "unavailable")
            )

        val isAptGet = executable.substringAfterLast('/') == "apt-get"
        val args = buildList {
            add("install")
            add("-y")
            if (isAptGet) add("--no-install-recommends")
            add("--")
            addAll(packages)
        }

        val result = runner.run(
            CommandRequest(
                executable = executable,
                arguments = args,
                workingDirectory = context.workspace,
                timeoutMillis = 15 * 60_000L
            )
        )

        return ToolResponse(
            ok = result.succeeded,
            output = buildString {
                if (result.stdout.isNotBlank()) append(result.stdout.trimEnd())
                if (result.stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(result.stderr.trimEnd())
                }
            },
            metadata = mapOf(
                "exitCode" to result.exitCode.toString(),
                "packages" to packages.joinToString(","),
                "backend" to executable
            )
        )
    }
}
