package com.kankwj.angcode.runtime

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI

class NetworkPolicy {
    fun allow(url: String): PolicyDecision {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: return PolicyDecision(false, "URL inválida")
        if (uri.scheme !in setOf("http", "https")) {
            return PolicyDecision(false, "Solo se permite HTTP/HTTPS")
        }
        val host = uri.host ?: return PolicyDecision(false, "Host inválido")
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrElse {
            return PolicyDecision(false, "No se pudo resolver el host")
        }
        if (addresses.any { isPrivateOrLocal(it) }) {
            return PolicyDecision(false, "Red local/privada bloqueada por defecto")
        }
        return PolicyDecision(true, "permitido")
    }

    private fun isPrivateOrLocal(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return true

        val bytes = address.address
        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            if ((first and 0xfe) == 0xfc) return true
            if (first == 0xfe && (second and 0xc0) == 0x80) return true
        }
        return false
    }
}

class HttpGetTool(
    private val policy: NetworkPolicy = NetworkPolicy()
) : AgentTool {
    override val id = "http.get"
    override val description = "Descarga texto HTTP/HTTPS con límites de tamaño y bloqueo de red privada."
    override val requiredPermissions = setOf(ToolPermission.NETWORK)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val url = call.arguments["url"] ?: return ToolResponse(false, "Falta url")
        val maxBytes = call.arguments["maxBytes"]?.toIntOrNull()?.coerceIn(1, 2_000_000) ?: 500_000

        val decision = policy.allow(url)
        if (!decision.allowed) return ToolResponse(false, decision.reason)

        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", "AngCode/0.1")
        }

        return try {
            val status = connection.responseCode
            if (status in 300..399) {
                return ToolResponse(
                    false,
                    "Redirección bloqueada; valida el nuevo destino explícitamente",
                    mapOf("status" to status.toString(), "location" to (connection.getHeaderField("Location") ?: ""))
                )
            }

            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            if (stream == null) return ToolResponse(false, "Respuesta sin cuerpo", mapOf("status" to status.toString()))

            val output = stream.use { input ->
                val buffer = ByteArray(8_192)
                val bytes = java.io.ByteArrayOutputStream()
                while (bytes.size() <= maxBytes) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    bytes.write(buffer, 0, read)
                    if (bytes.size() > maxBytes) break
                }
                if (bytes.size() > maxBytes) return ToolResponse(false, "Respuesta supera el límite de " + maxBytes + " bytes")
                bytes.toString(Charsets.UTF_8.name())
            }

            ToolResponse(
                ok = status in 200..299,
                output = output,
                metadata = mapOf(
                    "status" to status.toString(),
                    "contentType" to (connection.contentType ?: "")
                )
            )
        } finally {
            connection.disconnect()
        }
    }
}
