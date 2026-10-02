package com.kankwj.angcode.connectors

import android.content.Context
import android.os.Build
import android.system.Os
import com.kankwj.angcode.runtime.CommandRequest
import com.kankwj.angcode.runtime.CommandRunner
import com.kankwj.angcode.runtime.ProcessRegistry
import com.kankwj.angcode.runtime.SandboxInstallTool
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.MessageDigest

data class LightpandaBinaryManifest(
    val architecture: String,
    val url: String,
    val sha256: String,
    val bytes: Long
)

data class LightpandaInstallResult(
    val success: Boolean,
    val path: String?,
    val sha256: String?,
    val detail: String
)

data class LightpandaStartResult(
    val success: Boolean,
    val processId: String?,
    val endpoint: String?,
    val detail: String,
    val registeredTools: Int = 0,
    val sandboxName: String? = null
)

data class LightpandaStatus(
    val installed: Boolean,
    val running: Boolean,
    val processId: String?,
    val endpoint: String?,
    val registeredTools: Int,
    val sandboxName: String?
)

class LightpandaBinaryStore(
    context: Context
) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "toolpacks/lightpanda").apply { mkdirs() }

    fun installedBinary(): File? {
        val file = File(root, "lightpanda")
        return file.takeIf { it.isFile && it.canExecute() }
    }

    fun manifestForDevice(): LightpandaBinaryManifest? {
        val abis = Build.SUPPORTED_ABIS.map(String::lowercase)
        return when {
            abis.any { it == "arm64-v8a" || it == "aarch64" } -> AARCH64
            abis.any { it == "x86_64" || it == "amd64" } -> X86_64
            else -> null
        }
    }

    fun install(): LightpandaInstallResult {
        val manifest = manifestForDevice()
            ?: return LightpandaInstallResult(false, null, null, "Arquitectura no soportada")

        val finalFile = File(root, "lightpanda")
        val temp = File(root, "lightpanda.download")

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L

            val connection = (URL(manifest.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "AngCode/0.2")
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                connection.disconnect()
                return LightpandaInstallResult(false, null, null, "HTTP " + status)
            }

            connection.inputStream.use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_BYTES) error("Descarga excede el límite permitido")
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            }
            connection.disconnect()

            if (total != manifest.bytes) {
                temp.delete()
                return LightpandaInstallResult(
                    false, null, null,
                    "Tamaño inesperado: " + total + " bytes"
                )
            }

            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha != manifest.sha256) {
                temp.delete()
                return LightpandaInstallResult(
                    false, null, sha, "SHA-256 de Lightpanda no coincide"
                )
            }

            finalFile.delete()
            if (!temp.renameTo(finalFile)) {
                temp.copyTo(finalFile, overwrite = true)
                temp.delete()
            }
            Os.chmod(finalFile.absolutePath, 0x1C0) // 0700

            LightpandaInstallResult(
                true,
                finalFile.absolutePath,
                sha,
                "Lightpanda instalado y verificado"
            )
        } catch (error: Throwable) {
            temp.delete()
            LightpandaInstallResult(false, null, null, error.message ?: "Falló la instalación")
        }
    }

    companion object {
        private const val MAX_BYTES = 260L * 1024L * 1024L

        val AARCH64 = LightpandaBinaryManifest(
            architecture = "aarch64",
            url = "https://github.com/lightpanda-io/browser/releases/download/nightly/lightpanda-aarch64-linux",
            sha256 = "ddf7bec179435c78c47b3f629c4c658839168528c341d741cf9dc91b844b7f0a",
            bytes = 192_539_528L
        )

        val X86_64 = LightpandaBinaryManifest(
            architecture = "x86_64",
            url = "https://github.com/lightpanda-io/browser/releases/download/nightly/lightpanda-x86_64-linux",
            sha256 = "15d8ce794c9f88c177157688a5ffddef89b311d59f1aec06dca81ae2ccc0354c",
            bytes = 188_199_792L
        )
    }
}

class LightpandaRuntimeManager(
    context: Context,
    private val processRegistry: ProcessRegistry = sharedProcesses,
    private val runner: CommandRunner = CommandRunner()
) {
    private val appContext = context.applicationContext
    private val binaryStore = LightpandaBinaryStore(appContext)

    fun install(): LightpandaInstallResult = binaryStore.install()

    fun startAndRegister(
        context: ToolContext,
        broker: ToolBroker,
        sandboxName: String = DEFAULT_SANDBOX,
        image: String = DEFAULT_IMAGE,
        port: Int = DEFAULT_PORT
    ): LightpandaStartResult {
        val sandbox = ensureSandbox(context, sandboxName, image)
        if (sandbox != null) {
            return LightpandaStartResult(false, null, null, sandbox, sandboxName = sandboxName)
        }

        val started = start(context, sandboxName, port)
        if (!started.success) return started

        val client = ConnectorSessionRegistry.get(SESSION_ID)
            ?: return stopAfterRegistrationFailure(
                broker, "MCP arrancó pero no existe sesión registrada"
            )

        return runCatching {
            val info = broker.connectMcp(
                client = client,
                namespace = "browser",
                permissions = setOf(
                    ToolPermission.NETWORK,
                    ToolPermission.PRIVATE_NETWORK,
                    ToolPermission.MCP_EXTERNAL
                )
            )
            currentToolIds = info.registeredTools.toSet()
            started.copy(
                detail = "Lightpanda MCP listo · " + info.registeredTools.size + " herramientas",
                registeredTools = info.registeredTools.size,
                sandboxName = sandboxName
            )
        }.getOrElse { error ->
            stopAfterRegistrationFailure(
                broker,
                error.message ?: "No se pudieron registrar herramientas MCP"
            )
        }
    }

    fun start(
        context: ToolContext,
        sandboxName: String = DEFAULT_SANDBOX,
        port: Int = DEFAULT_PORT
    ): LightpandaStartResult {
        require(port in 1024..65535)

        val binary = binaryStore.installedBinary()
            ?: return LightpandaStartResult(false, null, null, "Lightpanda no está instalado")
        val prootDistro = context.executables["proot-distro"]
            ?: return LightpandaStartResult(false, null, null, "proot-distro no está disponible")

        currentProcessId?.let { id ->
            val snapshot = processRegistry.snapshot(id)
            if (snapshot?.running == true) {
                return LightpandaStartResult(
                    true,
                    id,
                    currentEndpoint ?: endpoint(port),
                    "Lightpanda ya estaba ejecutándose",
                    registeredTools = currentToolIds.size,
                    sandboxName = currentSandboxName ?: sandboxName
                )
            }
        }

        val guestBinary = "/tmp/angcode-lightpanda"
        val args = listOf(
            "login",
            "--isolated",
            "--minimal",
            "--bind",
            binary.canonicalPath + ":" + guestBinary,
            sandboxName,
            "--",
            "/usr/bin/env",
            "LIGHTPANDA_DISABLE_TELEMETRY=true",
            "LIGHTPANDA_DISABLE_CORE_DUMP=1",
            guestBinary,
            "mcp",
            "--host",
            "127.0.0.1",
            "--port",
            port.toString()
        )

        val id = processRegistry.start(
            CommandRequest(
                executable = prootDistro,
                arguments = args,
                workingDirectory = context.workspace,
                environment = prootEnvironment(prootDistro)
            )
        )
        currentProcessId = id
        currentSandboxName = sandboxName
        currentEndpoint = endpoint(port)

        if (!waitForPort(port, 30_000)) {
            val logs = processRegistry.snapshot(id)
            processRegistry.stop(id)
            clearRuntimeState()
            return LightpandaStartResult(
                false,
                id,
                null,
                "Lightpanda no abrió el puerto. " +
                    (logs?.stderr?.takeLast(1200) ?: ""),
                sandboxName = sandboxName
            )
        }

        val endpoint = endpoint(port)
        val client = OfficialMcpHttpClient(endpoint)
        return runCatching {
            client.connect()
            ConnectorSessionRegistry.put(SESSION_ID, client)
            LightpandaStartResult(
                true,
                id,
                endpoint,
                "Lightpanda MCP conectado",
                sandboxName = sandboxName
            )
        }.getOrElse { error ->
            processRegistry.stop(id)
            clearRuntimeState()
            LightpandaStartResult(
                false,
                id,
                null,
                error.message ?: "No se pudo conectar MCP",
                sandboxName = sandboxName
            )
        }
    }

    fun stop(broker: ToolBroker? = null): Boolean {
        broker?.unregisterAll(currentToolIds)
        currentToolIds = emptySet()
        ConnectorSessionRegistry.remove(SESSION_ID)

        val id = currentProcessId
        clearRuntimeState()
        return id == null || processRegistry.stop(id)
    }

    fun status(): LightpandaStatus =
        LightpandaStatus(
            installed = binaryStore.installedBinary() != null,
            running = running(),
            processId = currentProcessId,
            endpoint = currentEndpoint,
            registeredTools = currentToolIds.size,
            sandboxName = currentSandboxName
        )

    fun running(): Boolean =
        currentProcessId?.let { processRegistry.snapshot(it)?.running } == true

    private fun ensureSandbox(
        context: ToolContext,
        sandboxName: String,
        image: String
    ): String? {
        val prootDistro = context.executables["proot-distro"]
            ?: return "proot-distro no está disponible"

        val list = runner.run(
            CommandRequest(
                executable = prootDistro,
                arguments = listOf("list", "--quiet"),
                workingDirectory = context.workspace,
                timeoutMillis = 30_000,
                environment = prootEnvironment(prootDistro)
            )
        )

        if (!list.succeeded) {
            return "No se pudo consultar sandboxes: " +
                list.stderr.ifBlank { "exit=" + list.exitCode }
        }

        val installed = list.stdout.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()

        if (sandboxName in installed) return null

        val install = SandboxInstallTool().invoke(
            ToolCall(
                "sandbox.install",
                mapOf("image" to image, "name" to sandboxName)
            ),
            context
        )
        return if (install.ok) null else install.output
    }

    private fun stopAfterRegistrationFailure(
        broker: ToolBroker,
        detail: String
    ): LightpandaStartResult {
        val id = currentProcessId
        stop(broker)
        return LightpandaStartResult(
            false,
            id,
            null,
            detail,
            sandboxName = currentSandboxName
        )
    }

    private fun endpoint(port: Int) = "http://127.0.0.1:" + port + "/mcp"

    private fun waitForPort(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ok = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 400)
                }
                true
            }.getOrDefault(false)
            if (ok) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun prootEnvironment(executable: String): Map<String, String> {
        val bin = File(executable).parentFile ?: return emptyMap()
        val prefix = bin.parentFile ?: return emptyMap()
        val files = prefix.parentFile ?: return emptyMap()
        return mapOf(
            "PREFIX" to prefix.absolutePath,
            "HOME" to File(files, "home").apply { mkdirs() }.absolutePath,
            "TMPDIR" to File(prefix, "tmp").apply { mkdirs() }.absolutePath,
            "PATH" to (bin.absolutePath + ":" + System.getenv("PATH").orEmpty()),
            "LANG" to "C.UTF-8"
        )
    }

    private fun clearRuntimeState() {
        currentProcessId = null
        currentEndpoint = null
        currentSandboxName = null
    }

    companion object {
        const val DEFAULT_SANDBOX = "angcode-browser"
        const val DEFAULT_IMAGE = "debian:bookworm-slim"
        const val DEFAULT_PORT = 9223
        private const val SESSION_ID = "browser"

        private val sharedProcesses = ProcessRegistry()
        @Volatile private var currentProcessId: String? = null
        @Volatile private var currentEndpoint: String? = null
        @Volatile private var currentSandboxName: String? = null
        @Volatile private var currentToolIds: Set<String> = emptySet()
    }
}
