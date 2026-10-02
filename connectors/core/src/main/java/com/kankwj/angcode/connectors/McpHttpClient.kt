package com.kankwj.angcode.connectors

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchemaJson: String
)

data class McpBinaryContent(
    val dataBase64: String,
    val mimeType: String,
    val name: String? = null,
    val sourceUri: String? = null
)

data class McpCallResult(
    val text: String,
    val isError: Boolean,
    val imageCount: Int,
    val rawJson: String,
    val images: List<McpBinaryContent> = emptyList(),
    val binaryContents: List<McpBinaryContent> = images
)

private const val MAX_RESPONSE_BYTES = 64 * 1024 * 1024

class McpProtocolException(message: String) : IllegalStateException(message)

/**
 * Small MCP Streamable-HTTP client for local/remote tool servers.
 * Lightpanda's HTTP MCP endpoint is the first backend, but this client is
 * intentionally generic.
 */
class McpHttpClient(
    private val endpoint: String,
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 30_000
) {
    private val ids = AtomicLong(1)

    @Volatile
    var sessionId: String? = null
        private set

    @Volatile
    var protocolVersion: String? = null
        private set

    fun initialize(
        clientName: String = "AngCode",
        clientVersion: String = "0.1.0",
        requestedProtocol: String = "2025-11-25"
    ): String {
        val params = JSONObject()
            .put("protocolVersion", requestedProtocol)
            .put("capabilities", JSONObject())
            .put(
                "clientInfo",
                JSONObject()
                    .put("name", clientName)
                    .put("version", clientVersion)
            )

        val response = requireResponse(post(request("initialize", params)))
        throwIfError(response)
        val result = response.optJSONObject("result")
            ?: throw McpProtocolException("initialize sin result")

        protocolVersion = result.optString("protocolVersion", requestedProtocol)

        // MCP initialization handshake notification.
        post(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/initialized")
                .put("params", JSONObject())
        )

        return protocolVersion ?: requestedProtocol
    }

    fun listTools(): List<McpToolDefinition> {
        val response = requireResponse(post(request("tools/list", JSONObject())))
        throwIfError(response)
        val tools = response.optJSONObject("result")
            ?.optJSONArray("tools")
            ?: JSONArray()

        return buildList {
            for (index in 0 until tools.length()) {
                val tool = tools.optJSONObject(index) ?: continue
                val name = tool.optString("name")
                if (name.isBlank()) continue
                add(
                    McpToolDefinition(
                        name = name,
                        description = tool.optString("description", tool.optString("title", name)),
                        inputSchemaJson = tool.optJSONObject("inputSchema")?.toString()
                            ?: JSONObject().put("type", "object").toString()
                    )
                )
            }
        }
    }

    fun callTool(name: String, arguments: Map<String, String>): McpCallResult {
        val params = JSONObject()
            .put("name", name)
            .put("arguments", arguments.toJsonObject())

        val response = requireResponse(post(request("tools/call", params)))
        throwIfError(response)
        val result = response.optJSONObject("result")
            ?: throw McpProtocolException("tools/call sin result")

        val content = result.optJSONArray("content") ?: JSONArray()
        val textParts = mutableListOf<String>()
        val binaries = mutableListOf<McpBinaryContent>()

        for (index in 0 until content.length()) {
            val item = content.optJSONObject(index) ?: continue
            when (item.optString("type")) {
                "text" -> item.optString("text")
                    .takeIf { it.isNotBlank() }
                    ?.let(textParts::add)

                "image", "audio" -> {
                    val data = item.optString("data")
                    val mime = item.optString("mimeType")
                    if (data.isNotBlank() && mime.isNotBlank()) {
                        binaries += McpBinaryContent(
                            dataBase64 = data,
                            mimeType = mime
                        )
                    }
                }

                "resource" -> {
                    val resource = item.optJSONObject("resource") ?: continue
                    resource.optString("text")
                        .takeIf { it.isNotBlank() }
                        ?.let(textParts::add)

                    val blob = resource.optString("blob")
                    if (blob.isNotBlank()) {
                        binaries += McpBinaryContent(
                            dataBase64 = blob,
                            mimeType = resource.optString("mimeType", "application/octet-stream"),
                            name = resource.optString("name").takeIf { it.isNotBlank() },
                            sourceUri = resource.optString("uri").takeIf { it.isNotBlank() }
                        )
                    }
                }
            }
        }

        return McpCallResult(
            text = textParts.joinToString("\n"),
            isError = result.optBoolean("isError", false),
            imageCount = binaries.count { it.mimeType.startsWith("image/", ignoreCase = true) },
            rawJson = response.toString(),
            images = binaries.filter { it.mimeType.startsWith("image/", ignoreCase = true) },
            binaryContents = binaries
        )
    }

    fun closeSession(): Boolean {
        val id = sessionId ?: return true
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "DELETE"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Mcp-Session-Id", id)
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
            sessionId = null
        }
    }

    private fun request(method: String, params: JSONObject): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", ids.getAndIncrement())
            .put("method", method)
            .put("params", params)

    private fun post(payload: JSONObject): JSONObject? {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json, text/event-stream")
            sessionId?.let { connection.setRequestProperty("Mcp-Session-Id", it) }

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(payload.toString())
            }

            connection.getHeaderField("Mcp-Session-Id")
                ?.takeIf { it.isNotBlank() }
                ?.let { sessionId = it }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val buffer = ByteArray(16 * 1024)
                val output = java.io.ByteArrayOutputStream()
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_RESPONSE_BYTES) {
                        error("Respuesta MCP demasiado grande")
                    }
                }
                output.toString(Charsets.UTF_8.name())
            }.orEmpty()

            if (status !in 200..299) {
                throw McpProtocolException(
                    "MCP HTTP " + status + if (body.isBlank()) "" else ": " + body.take(2_000)
                )
            }

            val json = extractJson(body)
            if (json.isBlank()) null else JSONObject(json)
        } finally {
            connection.disconnect()
        }
    }

    private fun requireResponse(response: JSONObject?): JSONObject =
        response ?: throw McpProtocolException("Respuesta MCP vacía")

    private fun throwIfError(response: JSONObject) {
        if (!response.has("error")) return
        val error = response.optJSONObject("error")
        val code = error?.optInt("code")
        val message = error?.optString("message").orEmpty()
        throw McpProtocolException("MCP error " + (code ?: "?") + ": " + message)
    }

    private fun extractJson(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("{")) return trimmed

        // Streamable HTTP can use SSE. Use the first JSON data event.
        return trimmed.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("data:") }
            ?.removePrefix("data:")
            ?.trim()
            .orEmpty()
    }
}

private fun Map<String, String>.toJsonObject(): JSONObject {
    val objectValue = JSONObject()
    forEach { (key, value) ->
        objectValue.put(key, value.coerceJsonScalar())
    }
    return objectValue
}

private fun String.coerceJsonScalar(): Any {
    val trimmed = trim()
    if (trimmed.equals("true", true)) return true
    if (trimmed.equals("false", true)) return false
    if (trimmed.equals("null", true)) return JSONObject.NULL
    trimmed.toLongOrNull()?.let { return it }
    trimmed.toDoubleOrNull()?.let { return it }

    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        runCatching { JSONObject(trimmed) }.getOrNull()?.let { return it }
    }
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
        runCatching { JSONArray(trimmed) }.getOrNull()?.let { return it }
    }

    return this
}
