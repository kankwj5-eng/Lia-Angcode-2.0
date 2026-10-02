package com.kankwj.angcode.connectors

internal object McpArtifactNaming {
    fun extensionForMime(mimeType: String): String =
        when (mimeType.lowercase().substringBefore(';').trim()) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/svg+xml" -> "svg"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/ogg" -> "ogg"
            "audio/flac" -> "flac"
            "application/pdf" -> "pdf"
            "application/zip" -> "zip"
            "application/gzip", "application/x-gzip" -> "gz"
            "application/json" -> "json"
            "text/plain" -> "txt"
            "text/html" -> "html"
            "text/csv" -> "csv"
            else -> "bin"
        }

    fun safeToolName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(50)
            .ifBlank { "mcp" }
}
