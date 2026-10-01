package com.kankwj.angcode.connectors

/**
 * Stable AngCode-facing MCP contract. Protocol implementations can evolve
 * without changing McpProxyTool or the agent runtime.
 */
interface McpClientPort {
    val backendId: String
    val sessionId: String?

    fun connect(): String
    fun listTools(): List<McpToolDefinition>
    fun callTool(name: String, arguments: Map<String, String>): McpCallResult
    fun close(): Boolean
}

class LegacyMcpClientPort(
    private val delegate: McpHttpClient
) : McpClientPort {
    override val backendId: String = "legacy-http"
    override val sessionId: String?
        get() = delegate.sessionId

    override fun connect(): String = delegate.initialize()
    override fun listTools(): List<McpToolDefinition> = delegate.listTools()
    override fun callTool(name: String, arguments: Map<String, String>): McpCallResult =
        delegate.callTool(name, arguments)
    override fun close(): Boolean = delegate.closeSession()
}
