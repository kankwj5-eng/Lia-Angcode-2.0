package com.kankwj.angcode.runtime

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

data class LocalPreview(
    val port: Int,
    val url: String,
    val status: Int?,
    val title: String?
)

class PreviewProbe {
    fun probe(port: Int): LocalPreview? {
        require(port in 1024..65535)

        val open = runCatching {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress("127.0.0.1", port),
                    SOCKET_TIMEOUT_MS
                )
            }
            true
        }.getOrDefault(false)

        if (!open) return null

        val url = "http://127.0.0.1:" + port
        val connection = runCatching {
            URL(url).openConnection() as HttpURLConnection
        }.getOrNull()

        if (connection == null) {
            return LocalPreview(port, url, null, null)
        }

        return try {
            connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
            connection.readTimeout = HTTP_READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AngCode-Preview/0.2")

            val status = connection.responseCode
            val stream = if (status >= 400) {
                connection.errorStream
            } else {
                connection.inputStream
            }

            val body = stream?.bufferedReader()?.use { reader ->
                val chars = CharArray(MAX_HTML_CHARS)
                val count = reader.read(chars)
                if (count > 0) String(chars, 0, count) else ""
            }.orEmpty()

            val title = TITLE_REGEX.find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.take(120)

            LocalPreview(
                port = port,
                url = url,
                status = status,
                title = title
            )
        } catch (_: Throwable) {
            LocalPreview(port, url, null, null)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        val DEFAULT_PORTS = listOf(
            3000, 3001,
            4173, 5173,
            8000, 8080, 8081,
            8888, 9000,
            9222, 9223
        )

        private const val SOCKET_TIMEOUT_MS = 180
        private const val HTTP_CONNECT_TIMEOUT_MS = 500
        private const val HTTP_READ_TIMEOUT_MS = 700
        private const val MAX_HTML_CHARS = 32_768

        private val TITLE_REGEX = Regex(
            "<title[^>]*>(.*?)</title>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}

class PreviewProbeTool(
    private val probe: PreviewProbe = PreviewProbe()
) : AgentTool {
    override val id = "preview.probe"
    override val description =
        "Comprueba un puerto localhost y devuelve URL/status/título si hay preview."
    override val requiredPermissions = setOf(
        ToolPermission.PRIVATE_NETWORK
    )

    override fun invoke(
        call: ToolCall,
        context: ToolContext
    ): ToolResponse {
        val port = call.arguments["port"]
            ?.toIntOrNull()
            ?.takeIf { it in 1024..65535 }
            ?: return ToolResponse(false, "Puerto inválido")

        val preview = probe.probe(port)
            ?: return ToolResponse(
                false,
                "No hay servicio escuchando en 127.0.0.1:" + port
            )

        return ToolResponse(
            true,
            format(preview),
            metadata = mapOf(
                "port" to preview.port.toString(),
                "url" to preview.url,
                "status" to (preview.status?.toString() ?: ""),
                "title" to (preview.title ?: "")
            )
        )
    }
}

class PreviewScanTool(
    private val probe: PreviewProbe = PreviewProbe()
) : AgentTool {
    override val id = "preview.scan"
    override val description =
        "Busca previews HTTP en puertos localhost comunes o en una lista explícita."
    override val requiredPermissions = setOf(
        ToolPermission.PRIVATE_NETWORK
    )

    override fun invoke(
        call: ToolCall,
        context: ToolContext
    ): ToolResponse {
        val ports = parsePorts(call.arguments["ports"])
        val found = ports.mapNotNull(probe::probe)

        val output = found.joinToString("\n") { preview ->
            preview.port.toString() + "\t" +
                preview.url + "\t" +
                (preview.status?.toString() ?: "-") + "\t" +
                (preview.title ?: "")
        }

        return ToolResponse(
            true,
            output,
            metadata = mapOf(
                "count" to found.size.toString(),
                "urls" to found.joinToString(",") { it.url }
            )
        )
    }

    private fun parsePorts(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return PreviewProbe.DEFAULT_PORTS

        val ports = raw
            .split(',', ' ', '\u001F')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1024..65535 }
            .distinct()
            .take(MAX_PORTS)

        return ports.ifEmpty { PreviewProbe.DEFAULT_PORTS }
    }

    companion object {
        private const val MAX_PORTS = 24
    }
}

private fun format(preview: LocalPreview): String =
    buildString {
        appendLine("url=" + preview.url)
        appendLine("port=" + preview.port)
        appendLine("status=" + (preview.status ?: "-"))
        append("title=" + (preview.title ?: ""))
    }
