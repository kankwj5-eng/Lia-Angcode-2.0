package com.kankwj.angcode.connectors

import android.content.Context
import android.os.Build
import android.system.Os
import com.kankwj.angcode.runtime.CommandRequest
import com.kankwj.angcode.runtime.ProcessRegistry
import com.kankwj.angcode.runtime.ToolContext
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
    val detail: String
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
            abis.any { it == "arm64-v8a" || it == "aarch64" } ->
                AARCH64
            abis.any { it == "x86_64" || it == "amd64" } ->
                X86_64
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

            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha != manifest.sha256) {
                temp.delete()
                return LightpandaInstallResult(
                    false,
                    null,
                    sha,
                    "SHA-256 de Lightpanda no coincide"
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
    private val processRegistry: ProcessRegistry = sharedProcesses
) {
    private val appContext = context.applicationContext
    private val binaryStore = LightpandaBinaryStore(appContext)

    fun start(
        context: ToolContext,
        sandboxName: String = "angcode-browser",
        port: Int = 9223
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
                    endpoint(port),
                    "Lightpanda ya estaba ejecutándose"
                )
            }
        }

        val guestBinary = "/opt/angcode/lightpanda"
        val args = listOf(
            "login",
            "--isolated",
            "--minimal",
            "--bind",
            binary.canonicalPath + ":" + guestBinary,
            sandboxName,
            "--",
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

        if (!waitForPort(port, 20_000)) {
            val logs = processRegistry.snapshot(id)
            processRegistry.stop(id)
            currentProcessId = null
            return LightpandaStartResult(
                false,
                id,
                null,
                "Lightpanda no abrió el puerto. " +
                    (logs?.stderr?.takeLast(1200) ?: "")
            )
        }

        val endpoint = endpoint(port)
        val client = OfficialMcpHttpClient(endpoint)
        return runCatching {
            client.connect()
            ConnectorSessionRegistry.put("browser", client)
            LightpandaStartResult(
                true,
                id,
                endpoint,
                "Lightpanda MCP conectado"
            )
        }.getOrElse { error ->
            processRegistry.stop(id)
            currentProcessId = null
            LightpandaStartResult(
                false,
                id,
                null,
                error.message ?: "No se pudo conectar MCP"
            )
        }
    }

    fun stop(): Boolean {
        ConnectorSessionRegistry.remove("browser")
        val id = currentProcessId ?: return true
        currentProcessId = null
        return processRegistry.stop(id)
    }

    fun running(): Boolean =
        currentProcessId?.let { processRegistry.snapshot(it)?.running } == true

    fun processId(): String? = currentProcessId

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
            "LIGHTPANDA_DISABLE_TELEMETRY" to "true",
            "LANG" to "C.UTF-8"
        )
    }

    companion object {
        private val sharedProcesses = ProcessRegistry()
        @Volatile private var currentProcessId: String? = null
    }
}
