package com.kankwj.angcode.connectors

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * MCP client backed by the official Kotlin SDK.
 *
 * The SDK owns protocol negotiation and compatibility. AngCode only adapts
 * its tool model to ToolBroker.
 */
class OfficialMcpHttpClient(
    private val endpoint: String,
    private val clientName: String = "AngCode",
    private val clientVersion: String = "0.2.0"
) : McpClientPort {
    override val backendId: String = "official-kotlin-sdk-0.15.0"
    override val sessionId: String? = null

    private var httpClient: HttpClient? = null
    private var client: Client? = null

    override fun connect(): String = runBlocking {
        if (client != null) return@runBlocking "sdk-managed/0.15.0"

        val http = HttpClient(CIO) {
            install(SSE)
        }
        val mcp = Client(
            clientInfo = Implementation(
                name = clientName,
                version = clientVersion
            )
        )
        val transport = StreamableHttpClientTransport(
            client = http,
            url = endpoint
        )

        try {
            mcp.connect(transport)
            httpClient = http
            client = mcp
            "sdk-managed/0.15.0"
        } catch (error: Throwable) {
            http.close()
            throw error
        }
    }

    override fun listTools(): List<McpToolDefinition> = runBlocking {
        val mcp = requireClient()
        mcp.listTools().tools.map { tool ->
            McpToolDefinition(
                name = tool.name,
                description = tool.description ?: tool.title ?: tool.name,
                inputSchemaJson = Json.encodeToString(
                    ToolSchema.serializer(),
                    tool.inputSchema
                )
            )
        }
    }

    override fun callTool(
        name: String,
        arguments: Map<String, String>
    ): McpCallResult = runBlocking {
        val result = requireClient().callTool(
            name = name,
            arguments = arguments.mapValues { (_, value) -> value.coerceMcpValue() }
        )

        val text = result.content
            .filterIsInstance<TextContent>()
            .joinToString("\n") { it.text }
            .ifBlank { result.structuredContent?.toString().orEmpty() }

        val images = result.content
            .filterIsInstance<ImageContent>()
            .map { image ->
                McpBinaryContent(
                    dataBase64 = image.data,
                    mimeType = image.mimeType
                )
            }

        McpCallResult(
            text = text,
            isError = result.isError == true,
            imageCount = images.size,
            rawJson = result.toString(),
            images = images
        )
    }

    override fun close(): Boolean {
        val mcp = client
        val http = httpClient
        client = null
        httpClient = null

        return runCatching {
            if (mcp != null) {
                runBlocking { mcp.close() }
            }
            http?.close()
            true
        }.getOrDefault(false)
    }

    private fun requireClient(): Client =
        client ?: error("MCP no conectado. Llama connect() primero.")
}

private fun String.coerceMcpValue(): Any? {
    val value = trim()

    if (
        (value.startsWith("{") && value.endsWith("}")) ||
        (value.startsWith("[") && value.endsWith("]"))
    ) {
        runCatching {
            Json.parseToJsonElement(value).toPlainValue()
        }.getOrNull()?.let { return it }
    }

    if (value.equals("null", ignoreCase = true)) return null
    if (value.equals("true", ignoreCase = true)) return true
    if (value.equals("false", ignoreCase = true)) return false
    value.toLongOrNull()?.let { return it }
    value.toDoubleOrNull()?.let { return it }
    return this
}

private fun JsonElement.toPlainValue(): Any? =
    when (this) {
        JsonNull -> null
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            longOrNull != null -> longOrNull
            doubleOrNull != null -> doubleOrNull
            else -> content
        }
        is JsonArray -> map { it.toPlainValue() }
        is JsonObject -> mapValues { (_, value) -> value.toPlainValue() }
        else -> toString()
    }
