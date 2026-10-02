package com.kankwj.angcode.runtime

import java.io.File

private val ADB_SERIAL = Regex("^[A-Za-z0-9._:-]{1,200}$")
private val ADB_HOST = Regex("^[A-Za-z0-9._:-]{1,253}$")

private fun runAndroidExecutable(
    context: ToolContext,
    id: String,
    arguments: List<String>,
    timeoutMillis: Long = 120_000
): ToolResponse {
    val executable = context.executables[id]
        ?: return ToolResponse(false, "Ejecutable no disponible: " + id)

    val result = CommandRunner().run(
        CommandRequest(
            executable = executable,
            arguments = arguments,
            workingDirectory = context.workspace,
            timeoutMillis = timeoutMillis,
            environment = androidRuntimeEnvironment(executable)
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
            "executableId" to id
        )
    )
}

private fun androidRuntimeEnvironment(executable: String): Map<String, String> {
    val bin = File(executable).parentFile ?: return emptyMap()
    val prefix = bin.parentFile ?: return emptyMap()
    val files = prefix.parentFile ?: return emptyMap()
    return mapOf(
        "PREFIX" to prefix.absolutePath,
        "HOME" to File(files, "home").apply { mkdirs() }.absolutePath,
        "TMPDIR" to File(prefix, "tmp").apply { mkdirs() }.absolutePath,
        "PATH" to (bin.absolutePath + ":" + System.getenv("PATH").orEmpty()),
        "LD_LIBRARY_PATH" to File(prefix, "lib").absolutePath,
        "LANG" to "C.UTF-8"
    )
}

class AndroidToolchainProbeTool : AgentTool {
    override val id = "android.toolchain"
    override val description = "Muestra qué piezas del toolchain Android ARM están disponibles."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val ids = listOf(
            "java", "javac", "ecj", "gradle",
            "aapt", "aapt2", "d8", "r8", "apksigner",
            "adb", "fastboot", "zipalign"
        )
        val output = ids.joinToString("\n") { id ->
            id + "=" + (context.executables[id] ?: "missing")
        }
        val available = ids.count(context.executables::containsKey)
        return ToolResponse(
            true,
            output,
            mapOf("available" to available.toString(), "expected" to ids.size.toString())
        )
    }
}

class AndroidApkBadgingTool : AgentTool {
    override val id = "android.apk.badging"
    override val description = "Inspecciona package, versión y SDK de un APK con AAPT."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.PROCESS_EXECUTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val apk = safeWorkspaceFile(context.workspace, path)
        if (!apk.isFile || !apk.extension.equals("apk", true)) {
            return ToolResponse(false, "APK no encontrado")
        }
        val tool = when {
            "aapt" in context.executables -> "aapt"
            "aapt2" in context.executables -> "aapt2"
            else -> return ToolResponse(false, "aapt/aapt2 no está instalado")
        }
        return runAndroidExecutable(
            context,
            tool,
            listOf("dump", "badging", apk.absolutePath),
            60_000
        )
    }
}

class AndroidApkVerifyTool : AgentTool {
    override val id = "android.apk.verify"
    override val description = "Verifica firma y certificados de un APK mediante apksigner."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.PROCESS_EXECUTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val apk = safeWorkspaceFile(context.workspace, path)
        if (!apk.isFile || !apk.extension.equals("apk", true)) {
            return ToolResponse(false, "APK no encontrado")
        }
        return runAndroidExecutable(
            context,
            "apksigner",
            listOf("verify", "--verbose", "--print-certs", apk.absolutePath),
            60_000
        )
    }
}

class AndroidAdbDevicesTool : AgentTool {
    override val id = "android.adb.devices"
    override val description = "Lista dispositivos visibles para el cliente ADB del runtime."
    override val requiredPermissions = setOf(
        ToolPermission.PROCESS_EXECUTE,
        ToolPermission.ADB_REMOTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse =
        runAndroidExecutable(context, "adb", listOf("devices", "-l"), 30_000)
}

class AndroidAdbConnectTool : AgentTool {
    override val id = "android.adb.connect"
    override val description = "Conecta ADB a un host:puerto explícito."
    override val requiredPermissions = setOf(
        ToolPermission.PROCESS_EXECUTE,
        ToolPermission.NETWORK,
        ToolPermission.PRIVATE_NETWORK,
        ToolPermission.ADB_REMOTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val host = call.arguments["host"]?.takeIf { ADB_HOST.matches(it) }
            ?: return ToolResponse(false, "host faltante o inválido")
        val port = call.arguments["port"]?.toIntOrNull()?.coerceIn(1, 65535) ?: 5555
        return runAndroidExecutable(
            context,
            "adb",
            listOf("connect", host + ":" + port),
            30_000
        )
    }
}

class AndroidAdbLogcatTool : AgentTool {
    override val id = "android.adb.logcat"
    override val description = "Lee logcat de un dispositivo ADB seleccionado."
    override val requiredPermissions = setOf(
        ToolPermission.PROCESS_EXECUTE,
        ToolPermission.ADB_REMOTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val serial = call.arguments["serial"]?.takeIf { ADB_SERIAL.matches(it) }
        val lines = call.arguments["lines"]?.toIntOrNull()?.coerceIn(20, 3000) ?: 300
        val args = mutableListOf<String>()
        if (serial != null) args += listOf("-s", serial)
        args += listOf("logcat", "-d", "-t", lines.toString())
        return runAndroidExecutable(context, "adb", args, 60_000)
    }
}

class AndroidAdbInstallTool : AgentTool {
    override val id = "android.adb.install"
    override val description = "Instala un APK del workspace en un dispositivo ADB explícitamente autorizado."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.PROCESS_EXECUTE,
        ToolPermission.ADB_REMOTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val apk = safeWorkspaceFile(context.workspace, path)
        if (!apk.isFile || !apk.extension.equals("apk", true)) {
            return ToolResponse(false, "APK no encontrado")
        }
        val serial = call.arguments["serial"]?.takeIf { ADB_SERIAL.matches(it) }
        val replace = call.arguments["replace"]?.toBooleanStrictOrNull() ?: true

        val args = mutableListOf<String>()
        if (serial != null) args += listOf("-s", serial)
        args += "install"
        if (replace) args += "-r"
        args += apk.absolutePath

        return runAndroidExecutable(context, "adb", args, 5 * 60_000L)
    }
}
