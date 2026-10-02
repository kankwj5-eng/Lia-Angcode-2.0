package com.kankwj.angcode.agents

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val MODEL_HTTP_MAX_REQUEST_BYTES: Int = 2 * 1024 * 1024
internal const val MODEL_HTTP_MAX_RESPONSE_BYTES: Int = 4 * 1024 * 1024

internal fun readUtf8Bounded(
    input: InputStream?,
    maxBytes: Int
): String {
    if (input == null) return ""
    require(maxBytes in 1..16 * 1024 * 1024) {
        "Límite de lectura inválido"
    }

    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    input.use { source ->
        while (true) {
            val read = source.read(buffer)
            if (read <= 0) break
            if (output.size() + read > maxBytes) {
                error("Respuesta del modelo supera el límite de " + maxBytes + " bytes")
            }
            output.write(buffer, 0, read)
        }
    }
    return output.toString(Charsets.UTF_8.name())
}
