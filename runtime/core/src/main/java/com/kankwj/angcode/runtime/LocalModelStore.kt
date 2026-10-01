package com.kankwj.angcode.runtime

import android.content.Context
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

data class LocalModelDescriptor(
    val name: String,
    val path: String,
    val bytes: Long,
    val sha256: String?,
    val active: Boolean
)

data class ModelImportResult(
    val success: Boolean,
    val descriptor: LocalModelDescriptor?,
    val detail: String
)

class LocalModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "models").apply { mkdirs() }
    private val preferences = appContext.getSharedPreferences("angcode-models", Context.MODE_PRIVATE)

    fun importGguf(input: InputStream, preferredName: String): ModelImportResult {
        val safeName = sanitizeModelName(preferredName)
        val finalFile = File(root, safeName)
        val temp = File(root, ".import-" + UUID.randomUUID() + ".tmp")

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            val magic = ByteArray(4)
            var magicCount = 0

            input.use { source ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        if (magicCount < 4) {
                            val copy = minOf(read, 4 - magicCount)
                            System.arraycopy(buffer, 0, magic, magicCount, copy)
                            magicCount += copy
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        total += read
                    }
                }
            }

            if (magicCount != 4 || magic.toString(Charsets.US_ASCII) != "GGUF") {
                temp.delete()
                return ModelImportResult(false, null, "Archivo rechazado: no tiene cabecera GGUF")
            }

            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val target = uniqueTarget(finalFile)

            if (!temp.renameTo(target)) {
                temp.inputStream().use { source ->
                    target.outputStream().use { destination -> source.copyTo(destination) }
                }
                temp.delete()
            }

            File(target.absolutePath + ".sha256").writeText(sha + "\n")

            val descriptor = LocalModelDescriptor(
                name = target.name,
                path = target.absolutePath,
                bytes = total,
                sha256 = sha,
                active = activeName() == target.name
            )
            ModelImportResult(true, descriptor, "Modelo GGUF importado")
        } catch (error: Throwable) {
            temp.delete()
            ModelImportResult(false, null, error.message ?: error::class.java.simpleName)
        }
    }

    fun list(): List<LocalModelDescriptor> =
        root.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val shaFile = File(file.absolutePath + ".sha256")
                LocalModelDescriptor(
                    name = file.name,
                    path = file.absolutePath,
                    bytes = file.length(),
                    sha256 = shaFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.length == 64 },
                    active = activeName() == file.name
                )
            }

    fun activate(name: String): LocalModelDescriptor {
        val model = list().firstOrNull { it.name == name } ?: error("Modelo no encontrado: " + name)
        preferences.edit().putString(KEY_ACTIVE, model.name).apply()
        return model.copy(active = true)
    }

    fun active(): LocalModelDescriptor? {
        val name = activeName() ?: return null
        return list().firstOrNull { it.name == name }
    }

    fun remove(name: String): Boolean {
        val model = list().firstOrNull { it.name == name } ?: return false
        val file = File(model.path)
        val sha = File(file.absolutePath + ".sha256")
        val deleted = file.delete()
        sha.delete()
        if (activeName() == name) preferences.edit().remove(KEY_ACTIVE).apply()
        return deleted
    }

    private fun activeName(): String? = preferences.getString(KEY_ACTIVE, null)

    private fun sanitizeModelName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "model.gguf" }
        return if (base.endsWith(".gguf", ignoreCase = true)) base.take(120)
        else base.take(115) + ".gguf"
    }

    private fun uniqueTarget(preferred: File): File {
        if (!preferred.exists()) return preferred
        val stem = preferred.nameWithoutExtension
        for (index in 2..9999) {
            val candidate = File(root, stem + "-" + index + ".gguf")
            if (!candidate.exists()) return candidate
        }
        error("Demasiados modelos con el mismo nombre")
    }

    companion object {
        private const val KEY_ACTIVE = "active_model"
    }
}

class LocalModelListTool(private val store: LocalModelStore) : AgentTool {
    override val id = "model.list"
    override val description = "Lista modelos GGUF locales y cuál está activo."
    override val requiredPermissions = emptySet<ToolPermission>()

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val models = store.list()
        val output = models.joinToString("\n") { model ->
            (if (model.active) "* " else "  ") + model.name + "\t" + model.bytes + "\t" + (model.sha256 ?: "")
        }
        return ToolResponse(true, output, mapOf("count" to models.size.toString()))
    }
}

class LocalModelActivateTool(private val store: LocalModelStore) : AgentTool {
    override val id = "model.activate"
    override val description = "Selecciona un modelo GGUF local para futuras inferencias."
    override val requiredPermissions = setOf(ToolPermission.MODEL_MANAGE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val name = call.arguments["name"] ?: return ToolResponse(false, "Falta name")
        return runCatching {
            val model = store.activate(name)
            ToolResponse(true, "Modelo activo: " + model.name, mapOf("path" to model.path, "bytes" to model.bytes.toString()))
        }.getOrElse { ToolResponse(false, it.message ?: "No se pudo activar el modelo") }
    }
}

fun ToolBroker.registerModelTools(context: Context): ToolBroker = apply {
    val store = LocalModelStore(context)
    register(LocalModelListTool(store))
    register(LocalModelActivateTool(store))
}
