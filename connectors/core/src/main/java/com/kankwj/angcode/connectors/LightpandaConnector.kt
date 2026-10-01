package com.kankwj.angcode.connectors

enum class LightpandaTransport {
    MCP_STDIO,
    MCP_HTTP,
    CDP
}

data class LightpandaLaunchConfig(
    val binaryPath: String = "/usr/local/bin/lightpanda",
    val host: String = "127.0.0.1",
    val port: Int = 9223,
    val disableTelemetry: Boolean = true
) {
    init {
        require(port in 1..65535)
    }

    fun command(transport: LightpandaTransport): List<String> =
        when (transport) {
            LightpandaTransport.MCP_STDIO ->
                listOf(binaryPath, "mcp")

            LightpandaTransport.MCP_HTTP ->
                listOf(binaryPath, "mcp", "--host", host, "--port", port.toString())

            LightpandaTransport.CDP ->
                listOf(binaryPath, "serve", "--host", host, "--port", port.toString())
        }

    fun environment(): Map<String, String> =
        if (disableTelemetry) {
            mapOf("LIGHTPANDA_DISABLE_TELEMETRY" to "true")
        } else {
            emptyMap()
        }
}

object LightpandaCapabilities {
    val pageTools = linkedSetOf(
        "goto", "search", "markdown", "html", "screenshot", "links",
        "evaluate", "extract", "tree", "nodeDetails", "interactiveElements",
        "structuredData", "detectForms", "click", "fill", "scroll",
        "waitForSelector", "waitForScript", "waitForState", "hover", "press",
        "selectOption", "setChecked", "findElement", "consoleLogs", "getUrl",
        "getCookies", "getEnv"
    )

    val sessionTools = linkedSetOf(
        "session_new", "session_list", "session_close"
    )
}
