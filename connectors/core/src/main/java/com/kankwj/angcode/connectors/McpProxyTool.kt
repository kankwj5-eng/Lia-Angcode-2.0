package com.kankwj.angcode.connectors

import com.kankwj.angcode.runtime.AgentTool
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.ToolResponse

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
        return ToolResponse(
            ok = !result.isError,
            output = result.text.ifBlank {
                when {
                    result.imageCount > 0 ->
                        "Resultado MCP con " + result.imageCount + " imagen(es)."
                    else -> "(respuesta MCP sin texto)"
                }
            },
            metadata = mapOf(
                "mcpTool" to definition.name,
                "mcpBackend" to client.backendId,
                "sessionId" to (client.sessionId ?: ""),
                "imageCount" to result.imageCount.toString()
            )
        )
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
