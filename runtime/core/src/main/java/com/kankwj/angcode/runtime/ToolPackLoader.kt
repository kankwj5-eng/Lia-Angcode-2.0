package com.kankwj.angcode.runtime

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ToolPackEvaluation(
    val manifest: ToolPackManifest,
    val architectureCompatible: Boolean,
    val runtimeAvailable: Boolean,
    val missingPackages: List<String>,
    val activeTools: List<String>,
    val missingTools: List<String>
) {
    val ready: Boolean
        get() = architectureCompatible &&
            runtimeAvailable &&
            missingPackages.isEmpty() &&
            missingTools.isEmpty()
}

class ToolPackParser {
    fun parse(jsonText: String): ToolPackManifest {
        val root = Json.parseToJsonElement(jsonText).jsonObject

        val id = root.requiredString("id")
        require(id.matches(Regex("^[a-z0-9._-]+$"))) { "id inválido: $id" }

        val permissions = root.requiredStringList("permissions").map { raw ->
            runCatching { ToolPermission.valueOf(raw) }
                .getOrElse { error("Permiso desconocido: $raw") }
        }.toSet()

        val runtime = root.optionalString("runtime")
            ?.let { runCatching { ToolPackRuntime.valueOf(it) }.getOrElse { error("Runtime inválido: $it") } }
            ?: ToolPackRuntime.ANDROID_NATIVE

        val upstream = root["upstream"]?.jsonObject?.let { value ->
            UpstreamComponent(
                name = value.requiredString("name"),
                license = value.requiredString("license"),
                source = value.requiredString("source")
            )
        }

        return ToolPackManifest(
            id = id,
            name = root.requiredString("name"),
            version = root.requiredString("version"),
            description = root.optionalString("description").orEmpty(),
            requiredPermissions = permissions,
            packages = root.requiredStringList("packages"),
            exportedTools = root.requiredStringList("tools"),
            runtime = runtime,
            architectures = root.optionalStringList("architectures"),
            upstream = upstream
        )
    }

    private fun JsonObject.requiredString(name: String): String =
        optionalString(name)?.takeIf { it.isNotBlank() }
            ?: error("Falta $name")

    private fun JsonObject.optionalString(name: String): String? =
        (get(name) as? JsonPrimitive)?.content

    private fun JsonObject.requiredStringList(name: String): List<String> {
        val value = get(name) as? JsonArray ?: error("Falta $name")
        return value.map { it.jsonPrimitive.content }.also {
            require(it.distinct().size == it.size) { "$name contiene duplicados" }
        }
    }

    private fun JsonObject.optionalStringList(name: String): List<String> =
        (get(name) as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
}

class ToolPackEvaluator {
    fun evaluate(
        manifest: ToolPackManifest,
        architecture: String,
        availablePackages: Set<String>,
        registeredTools: Set<String>,
        prootReady: Boolean
    ): ToolPackEvaluation {
        val normalizedArch = normalizeArchitecture(architecture)
        val architectureCompatible =
            manifest.architectures.isEmpty() ||
                manifest.architectures.any { normalizeArchitecture(it) == normalizedArch }

        val runtimeAvailable = when (manifest.runtime) {
            ToolPackRuntime.ANDROID_NATIVE -> true
            ToolPackRuntime.PROOT -> prootReady
        }

        val missingPackages = manifest.packages.filterNot(availablePackages::contains)
        val activeTools = manifest.exportedTools.filter { requested ->
            toolMatches(requested, registeredTools)
        }
        val missingTools = manifest.exportedTools - activeTools.toSet()

        return ToolPackEvaluation(
            manifest = manifest,
            architectureCompatible = architectureCompatible,
            runtimeAvailable = runtimeAvailable,
            missingPackages = missingPackages,
            activeTools = activeTools,
            missingTools = missingTools
        )
    }

    private fun toolMatches(requested: String, registered: Set<String>): Boolean {
        if (!requested.contains("*")) return requested in registered
        val prefix = requested.substringBefore("*")
        return registered.any { it.startsWith(prefix) }
    }

    private fun normalizeArchitecture(value: String): String =
        when (value.lowercase()) {
            "arm64-v8a", "aarch64" -> "aarch64"
            "armeabi-v7a", "arm", "armv7l" -> "arm"
            "x86_64", "amd64" -> "x86_64"
            "x86", "i686" -> "i686"
            else -> value.lowercase()
        }
}

class ToolPackAssetRepository(
    private val context: Context,
    private val parser: ToolPackParser = ToolPackParser()
) {
    fun loadAll(): List<ToolPackManifest> {
        return context.assets.list("")
            .orEmpty()
            .sorted()
            .mapNotNull { directory ->
                val path = "$directory/toolpack.json"
                runCatching {
                    context.assets.open(path).bufferedReader().use { parser.parse(it.readText()) }
                }.getOrNull()
            }
            .distinctBy { it.id }
    }

    fun load(id: String): ToolPackManifest? =
        loadAll().firstOrNull { it.id == id }
}
