package com.kankwj.angcode.runtime

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class RuntimeReleaseCandidate(
    val tag: String,
    val profile: String,
    val architecture: String,
    val zipName: String,
    val zipUrl: String,
    val shaUrl: String,
    val publishedAt: String
)

class RuntimeReleaseManager(
    context: Context
) {
    private val appContext = context.applicationContext

    fun findLatest(profile: String = "dev"): RuntimeReleaseCandidate {
        require(profile in setOf("minimal", "dev", "full")) {
            "Perfil runtime inválido"
        }
        val arch = deviceArchitecture()
            ?: error("Arquitectura del dispositivo no soportada")

        val releases = JSONArray(
            getText(
                "https://api.github.com/repos/" +
                    REPOSITORY + "/releases?per_page=40",
                accept = "application/vnd.github+json"
            )
        )

        val zipName = "angcode-bootstrap-" + profile + "-" + arch + ".zip"
        val shaName = zipName + ".sha256"

        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optBoolean("draft", false)) continue
            val tag = release.optString("tag_name")
            if (!tag.startsWith("runtime-" + profile + "-" + arch + "-")) {
                continue
            }

            val assets = release.optJSONArray("assets") ?: JSONArray()
            var zipUrl: String? = null
            var shaUrl: String? = null

            for (assetIndex in 0 until assets.length()) {
                val asset = assets.optJSONObject(assetIndex) ?: continue
                when (asset.optString("name")) {
                    zipName -> zipUrl = asset.optString("browser_download_url")
                    shaName -> shaUrl = asset.optString("browser_download_url")
                }
            }

            if (!zipUrl.isNullOrBlank() && !shaUrl.isNullOrBlank()) {
                return RuntimeReleaseCandidate(
                    tag = tag,
                    profile = profile,
                    architecture = arch,
                    zipName = zipName,
                    zipUrl = zipUrl,
                    shaUrl = shaUrl,
                    publishedAt = release.optString("published_at")
                )
            }
        }

        error(
            "No existe todavía un runtime público " +
                profile + "/" + arch
        )
    }

    fun installLatest(profile: String = "dev"): RuntimeInstallResult {
        val release = findLatest(profile)
        val shaText = getText(release.shaUrl, accept = "text/plain")
        val expectedSha = shaText
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.lowercase()
            ?.takeIf { it.matches(Regex("^[0-9a-f]{64}$")) }
            ?: error("Release runtime con SHA-256 inválido")

        val temp = File(
            appContext.cacheDir,
            release.zipName + ".download"
        )
        temp.delete()

        try {
            downloadFile(release.zipUrl, temp)
            return RuntimeBootstrapInstaller(appContext).install(
                bootstrap = temp.inputStream().buffered(),
                expectedSha256 = expectedSha
            )
        } finally {
            temp.delete()
        }
    }

    fun deviceArchitecture(): String? {
        val abis = Build.SUPPORTED_ABIS.map(String::lowercase)
        return when {
            abis.any { it == "arm64-v8a" || it == "aarch64" } -> "aarch64"
            abis.any { it == "armeabi-v7a" || it == "arm" || it == "armv7l" } -> "arm"
            abis.any { it == "x86_64" || it == "amd64" } -> "x86_64"
            abis.any { it == "x86" || it == "i686" } -> "i686"
            else -> null
        }
    }

    private fun downloadFile(
        source: String,
        destination: File
    ) {
        val connection = open(source, "application/octet-stream")
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                error("Runtime HTTP " + status)
            }

            val announced = connection.contentLengthLong
            if (announced > MAX_RUNTIME_BYTES) {
                error("Runtime remoto excede el límite permitido")
            }

            var total = 0L
            connection.inputStream.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_RUNTIME_BYTES) {
                            error("Runtime descargado excede el límite permitido")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            require(total > 0L) {
                "Runtime descargado vacío"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun getText(
        source: String,
        accept: String
    ): String {
        val connection = open(source, accept)
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (status !in 200..299) {
                error(
                    "GitHub/runtime HTTP " + status +
                        if (body.isBlank()) "" else ": " + body.take(500)
                )
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        source: String,
        accept: String
    ): HttpURLConnection =
        (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "AngCode/0.2")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

    companion object {
        private const val REPOSITORY =
            "kankwj5-eng/Lia-Angcode-2.0"
        private const val MAX_RUNTIME_BYTES =
            2_500L * 1024L * 1024L
    }
}
