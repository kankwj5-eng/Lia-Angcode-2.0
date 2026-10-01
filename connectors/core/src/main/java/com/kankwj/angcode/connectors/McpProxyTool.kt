package com.kankwj.angcode.connectors

import com.kankwj.angcode.runtime.AgentTool
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.ToolResponse

class McpProxyTool(
    private val client: McpHttpClient,
    private val definition: McpToolDefinition,
    namespace: String,
    override val requiredPermissions: Set<ToolPermission> = setOf(
        ToolPermission.NETWORK,
        ToolPermission.PRIVATE_NETWORK
    )
) : AgentTool {
    override val id: String = namespace.trimEnd('.') + "." + definition.name
    override val description: String = definition.description

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val result = client.callTool(definition.name, call.arguments)
        return ToolResponse(
            ok = !result.isError,
            output = result.text.ifBlank {
                if (result.imageCount > 0) {
                    "Resultado MCP con " + result.imageCount + " imagen(es)."
                } else {
                    "(respuesta MCP sin texto)"
                }
            },
            metadata = mapOf(
                "mcpTool" to definition.name,
                "sessionId" to (client.sessionId ?: ""),
                "imageCount" to result.imageCount.toString()
            )
        )
    }
}

data class McpConnectionInfo(
    val protocolVersion: String,
    val sessionId: String?,
    val registeredTools: List<String>
)

fun ToolBroker.connectMcpHttp(
    client: McpHttpClient,
    namespace: String = "mcp",
    permissions: Set<ToolPermission> = setOf(
        ToolPermission.NETWORK,
        ToolPermission.PRIVATE_NETWORK
    )
): McpConnectionInfo {
    val protocol = client.initialize()
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
        registeredTools = ids
    )
}
