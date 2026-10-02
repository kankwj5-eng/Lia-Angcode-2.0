package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.CommandRequest
import com.kankwj.angcode.runtime.ManagedProcessSnapshot
import com.kankwj.angcode.runtime.ProcessRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

data class LlamaServerHandle(
    val endpoint: String,
    val processId: String,
    val modelPath: String,
    val port: Int,
    val contextSize: Int,
    val threads: Int,
    val batchSize: Int
)

object LlamaServerManager {
    private val registry = ProcessRegistry()
    private val lock = Any()

    @Volatile
    private var current: LlamaServerHandle? = null

    fun ensureRunning(
        executable: File,
        model: File,
        contextSize: Int = 4_096,
        threads: Int = 4,
        batchSize: Int = 256,
        loadTimeoutMillis: Long = 3 * 60_000L
    ): LlamaServerHandle = synchronized(lock) {
        require(executable.isFile && executable.canExecute()) {
            "llama-server no está disponible"
        }
        require(model.isFile) {
            "Modelo GGUF no encontrado"
        }

        current?.let { handle ->
            val snapshot = registry.snapshot(handle.processId)
            if (
                snapshot?.running == true &&
                File(handle.modelPath).canonicalPath == model.canonicalPath &&
                handle.contextSize == contextSize.coerceIn(512, 32_768) &&
                handle.threads == threads.coerceIn(1, 16) &&
                handle.batchSize == batchSize.coerceIn(32, 2_048) &&
                health(handle.endpoint)
            ) {
                return@synchronized handle
            }
            registry.stop(handle.processId)
            current = null
        }

        val port = freePort()
        val processId = registry.start(
            CommandRequest(
                executable = executable.absolutePath,
                arguments = listOf(
                    "-m", model.absolutePath,
                    "--host", "127.0.0.1",
                    "--port", port.toString(),
                    "-c", contextSize.coerceIn(512, 32_768).toString(),
                    "-t", threads.coerceIn(1, 16).toString(),
                    "-b", batchSize.coerceIn(32, 2_048).toString(),
                    "--parallel", "1"
                ),
                workingDirectory = model.parentFile,
                environment = runtimeEnvironment(executable)
            )
        )

        val endpoint = "http://127.0.0.1:" + port
        val deadline = System.currentTimeMillis() + loadTimeoutMillis

        while (System.currentTimeMillis() < deadline) {
            val snapshot = registry.snapshot(processId)
            if (snapshot?.running != true) {
                current = null
                error(
                    "llama-server terminó al cargar: " +
                        ((snapshot?.stderr ?: snapshot?.stdout).orEmpty().takeLast(1600))
                )
            }

            if (health(endpoint)) {
                val handle = LlamaServerHandle(
                    endpoint = endpoint,
                    processId = processId,
                    modelPath = model.canonicalPath,
                    port = port,
                    contextSize = contextSize.coerceIn(512, 32_768),
                    threads = threads.coerceIn(1, 16),
                    batchSize = batchSize.coerceIn(32, 2_048)
                )
                current = handle
                return@synchronized handle
            }

            Thread.sleep(250)
        }

        val snapshot = registry.snapshot(processId)
        registry.stop(processId)
        current = null
        error(
            "Timeout cargando llama-server. " +
                ((snapshot?.stderr ?: snapshot?.stdout).orEmpty().takeLast(1600))
        )
    }

    fun status(): LlamaServerHandle? = synchronized(lock) {
        val handle = current ?: return@synchronized null
        val snapshot = registry.snapshot(handle.processId)
        if (snapshot?.running == true && health(handle.endpoint)) {
            handle
        } else {
            current = null
            null
        }
    }

    fun stop(): Boolean = synchronized(lock) {
        val handle = current ?: return@synchronized true
        current = null
        registry.stop(handle.processId)
    }

    fun logs(): ManagedProcessSnapshot? = synchronized(lock) {
        current?.let { registry.snapshot(it.processId) }
    }

    private fun freePort(): Int =
        ServerSocket(0).use { it.localPort }

    private fun health(endpoint: String): Boolean {
        val connection = runCatching {
            URL(endpoint + "/health").openConnection() as HttpURLConnection
        }.getOrNull() ?: return false

        return try {
            connection.connectTimeout = 500
            connection.readTimeout = 1_000
            connection.requestMethod = "GET"
            connection.responseCode == 200
        } catch (_: Throwable) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun runtimeEnvironment(executable: File): Map<String, String> {
        val bin = executable.parentFile ?: return emptyMap()
        val prefix = bin.parentFile ?: return emptyMap()
        val filesDir = prefix.parentFile ?: return emptyMap()

        return mapOf(
            "PREFIX" to prefix.absolutePath,
            "HOME" to File(filesDir, "home").apply { mkdirs() }.absolutePath,
            "TMPDIR" to File(prefix, "tmp").apply { mkdirs() }.absolutePath,
            "PATH" to (bin.absolutePath + ":" + System.getenv("PATH").orEmpty()),
            "LD_LIBRARY_PATH" to File(prefix, "lib").absolutePath,
            "LANG" to "C.UTF-8"
        )
    }
}

class LlamaServerModelGateway(
    private val handle: LlamaServerHandle,
    private val temperature: Double = 0.2,
    private val timeoutMillis: Int = 5 * 60_000
) : ModelGateway {
    override fun complete(request: ModelRequest): ModelResponse {
        val body = buildJsonObject {
            put("model", "local")
            put("stream", false)
            put("temperature", temperature.coerceIn(0.0, 2.0))
            put("max_tokens", request.maxOutputTokens.coerceIn(32, 4096))
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", request.system.trim())
                        }
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", ModelToolProtocol.prompt(request))
                        }
                    )
                }
            )
            put(
                "response_format",
                buildJsonObject {
                    put("type", "json_object")
                }
            )
        }

        val connection = URL(
            handle.endpoint + "/v1/chat/completions"
        ).openConnection() as HttpURLConnection

        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = timeoutMillis
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")

            val requestBytes = body.toString().toByteArray(Charsets.UTF_8)
            if (requestBytes.size > MODEL_HTTP_MAX_REQUEST_BYTES) {
                return ModelResponse(text = "Solicitud al modelo demasiado grande")
            }
            connection.setFixedLengthStreamingMode(requestBytes.size)
            connection.outputStream.use { output ->
                output.write(requestBytes)
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val raw = readUtf8Bounded(
                input = stream,
                maxBytes = MODEL_HTTP_MAX_RESPONSE_BYTES
            )
            if (status !in 200..299) {
                return ModelResponse(
                    text = "llama-server HTTP " + status + ": " + raw.take(2000)
                )
            }

            val root = Json.parseToJsonElement(raw).jsonObject
            val choices = root["choices"]?.jsonArray
                ?: return ModelResponse(text = "Respuesta llama-server sin choices")
            val first = choices.firstOrNull()?.jsonObject
                ?: return ModelResponse(text = "Respuesta llama-server vacía")
            val content = first["message"]
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()

            ModelToolProtocol.parse(content)
        } catch (error: Throwable) {
            ModelResponse(
                text = "Error hablando con llama-server: " +
                    (error.message ?: error::class.java.simpleName)
            )
        } finally {
            connection.disconnect()
        }
    }
}
