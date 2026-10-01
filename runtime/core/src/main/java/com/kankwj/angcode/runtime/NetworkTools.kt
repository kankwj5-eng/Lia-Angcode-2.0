package com.kankwj.angcode.runtime

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI

class HttpGetTool : AgentTool {
    override val id = "http.get"
    override val description = "Descarga texto o JSON por HTTP(S) con límite de tamaño."
    override val requiredPermissions = setOf(ToolPermission.NETWORK)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val raw = call.arguments["url"] ?: return ToolResponse(false, "Falta url")
        val uri = runCatching { URI(raw) }.getOrElse { return ToolResponse(false, "URL inválida") }
        if (uri.scheme !in setOf("http", "https")) return ToolResponse(false, "Solo HTTP(S)")
        val host = uri.host ?: return ToolResponse(false, "Host inválido")

        val addresses = runCatching { InetAddress.getAllByName(host).toList() }
            .getOrElse { return ToolResponse(false, "No se pudo resolver el host") }

        val privateTarget = addresses.any {
            it.isAnyLocalAddress || it.isLoopbackAddress || it.isSiteLocalAddress ||
                it.isLinkLocalAddress || it.isMulticastAddress
        }
        if (privateTarget && ToolPermission.PRIVATE_NETWORK !in context.grantedPermissions) {
            return ToolResponse(false, "Red local/privada bloqueada; requiere PRIVATE_NETWORK")
        }

        val maxBytes = call.arguments["maxBytes"]?.toIntOrNull()?.coerceIn(1024, 2_000_000) ?: 500_000
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AngCode/0.2")
            setRequestProperty("Accept", "text/*, application/json, application/xml;q=0.9, */*;q=0.2")
        }

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) return ToolResponse(false, "Respuesta supera $maxBytes bytes")
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            } ?: ByteArray(0)

            ToolResponse(
                ok = status in 200..299,
                output = bytes.toString(Charsets.UTF_8),
                metadata = mapOf(
                    "status" to status.toString(),
                    "contentType" to (connection.contentType ?: ""),
                    "bytes" to bytes.size.toString()
                )
            )
        } catch (e: Exception) {
            ToolResponse(false, e.message ?: "Error HTTP")
        } finally {
            connection.disconnect()
        }
    }
}
