package com.kankwj.angcode.connectors

import com.kankwj.angcode.runtime.AgentTool
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.ToolResponse
import java.io.File
import java.util.Base64

class McpProxyTool(
    private val client: McpClientPort,
    private val definition: McpToolDefinition,
    namespace: String,
    override val requiredPermissions: Set<ToolPermission> = setOf(
        ToolPermission.NETWORK,
        ToolPermission.MCP_EXTERNAL
    )
) : AgentTool {
    override val id: String = namespace.trimEnd('.') + "." + definition.name
    override val description: String = definition.description

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val result = client.callTool(definition.name, call.arguments)
        val artifactPaths = persistImages(result, context)

        val output = buildString {
            if (result.text.isNotBlank()) {
                append(result.text)
            }

            if (artifactPaths.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("Artifacts MCP:\n")
                artifactPaths.forEach { path ->
                    append("- ")
                    append(path)
                    append("\n")
                }
            } else if (result.imageCount > 0) {
                if (isNotEmpty()) append("\n")
                append(
                    if (ToolPermission.ARTIFACT_WRITE in context.grantedPermissions) {
                        "MCP devolvió imagen(es), pero no se pudieron persistir."
                    } else {
                        "MCP devolvió imagen(es); ARTIFACT_WRITE no fue concedido."
                    }
                )
            }

            if (isEmpty()) {
                append("(respuesta MCP sin texto)")
            }
        }.trimEnd()

        return ToolResponse(
            ok = !result.isError,
            output = output,
            metadata = mapOf(
                "mcpTool" to definition.name,
                "mcpBackend" to client.backendId,
                "sessionId" to (client.sessionId ?: ""),
                "imageCount" to result.imageCount.toString(),
                "artifactCount" to artifactPaths.size.toString(),
                "artifacts" to artifactPaths.joinToString(",")
            )
        )
    }

    private fun persistImages(
        result: McpCallResult,
        context: ToolContext
    ): List<String> {
        if (result.images.isEmpty()) return emptyList()
        if (ToolPermission.ARTIFACT_WRITE !in context.grantedPermissions) {
            return emptyList()
        }

        val workspace = context.workspace.canonicalFile
        val artifacts = File(workspace, "artifacts").canonicalFile
        require(artifacts.toPath().startsWith(workspace.toPath())) {
            "Ruta artifacts fuera del workspace"
        }
        artifacts.mkdirs()

        val safeTool = definition.name
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(50)
            .ifBlank { "mcp" }

        return result.images.mapIndexedNotNull { index, image ->
            runCatching {
                require(image.dataBase64.length <= MAX_BASE64_CHARS) {
                    "Imagen MCP demasiado grande"
                }
                val bytes = Base64.getDecoder().decode(image.dataBase64)
                require(bytes.size <= MAX_IMAGE_BYTES) {
                    "Imagen MCP decodificada demasiado grande"
                }

                val extension = when (image.mimeType.lowercase()) {
                    "image/png" -> "png"
                    "image/jpeg", "image/jpg" -> "jpg"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    "image/svg+xml" -> "svg"
                    else -> "bin"
                }

                val file = File(
                    artifacts,
                    "browser-" + safeTool + "-" +
                        System.currentTimeMillis() + "-" + index +
                        "." + extension
                ).canonicalFile

                require(file.toPath().startsWith(artifacts.toPath())) {
                    "Nombre de artifact MCP inválido"
                }
                file.writeBytes(bytes)
                file.relativeTo(workspace).invariantSeparatorsPath
            }.getOrNull()
        }
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 30 * 1024 * 1024
        private const val MAX_BASE64_CHARS = 42_000_000
    }
}

data class McpConnectionInfo(
    val protocolVersion: String,
    val sessionId: String?,
    val registeredTools: List<String>,
    val backendId: String
)

fun ToolBroker.connectMcp(
    client: McpClientPort,
    namespace: String = "mcp",
    permissions: Set<ToolPermission> = setOf(
        ToolPermission.NETWORK,
        ToolPermission.MCP_EXTERNAL
    )
): McpConnectionInfo {
    val protocol = client.connect()
    val ids = client.listTools().map { definition ->
        val proxy = McpProxyTool(
            client = client,
            definition = definition,
            namespace = namespace,
            requiredPermissions = permissions
        )
        register(proxy)
        proxy.id
    }

    return McpConnectionInfo(
        protocolVersion = protocol,
        sessionId = client.sessionId,
        registeredTools = ids,
        backendId = client.backendId
    )
}

/** Compatibility entry point for the hand-written 2025-era client. */
fun ToolBroker.connectMcpHttp(
    client: McpHttpClient,
    namespace: String = "mcp",
    permissions: Set<ToolPermission> = setOf(
        ToolPermission.NETWORK,
        ToolPermission.PRIVATE_NETWORK,
        ToolPermission.MCP_EXTERNAL
    )
): McpConnectionInfo =
    connectMcp(
        client = LegacyMcpClientPort(client),
        namespace = namespace,
        permissions = permissions
    )
