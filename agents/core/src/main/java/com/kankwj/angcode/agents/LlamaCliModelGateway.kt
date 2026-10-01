package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.CommandRequest
import com.kankwj.angcode.runtime.CommandRunner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class LlamaCliModelGateway(
    private val executable: File,
    private val model: File,
    private val runner: CommandRunner = CommandRunner(),
    private val temperature: Double = 0.2,
    private val timeoutMillis: Long = 5 * 60_000L
) : ModelGateway {
    init {
        require(executable.isFile && executable.canExecute()) { "llama-cli no está disponible" }
        require(model.isFile) { "Modelo GGUF no encontrado" }
    }

    override fun complete(request: ModelRequest): ModelResponse {
        val result = runner.run(
            CommandRequest(
                executable = executable.absolutePath,
                arguments = listOf(
                    "-m", model.absolutePath,
                    "-p", buildPrompt(request),
                    "-n", request.maxOutputTokens.coerceIn(32, 4096).toString(),
                    "--temp", temperature.coerceIn(0.0, 2.0).toString()
                ),
                workingDirectory = model.parentFile,
                timeoutMillis = timeoutMillis,
                environment = runtimeEnvironment(executable)
            )
        )

        if (!result.succeeded) {
            return ModelResponse(
                text = "Error de inferencia local: " +
                    result.stderr.ifBlank { "exit=" + result.exitCode }
            )
        }

        return parseResponse(result.stdout)
    }

    private fun buildPrompt(request: ModelRequest): String =
        buildString {
            appendLine("INSTRUCCIONES DEL SISTEMA")
            appendLine(request.system.trim())
            appendLine()
            appendLine("HERRAMIENTAS DISPONIBLES")
            if (request.tools.isEmpty()) appendLine("(ninguna)")
            else request.tools.forEach { appendLine("- " + it) }
            appendLine()
            appendLine("CONTEXTO / MENSAJE")
            appendLine(request.user.trim())
            appendLine()
            appendLine("FORMATO OBLIGATORIO")
            appendLine("Devuelve exactamente un objeto JSON y nada fuera de él.")
            appendLine("Para usar una herramienta:")
            appendLine("""{"type":"tool","tool":"file.read","arguments":{"path":"README.md"}}""")
            appendLine("Para terminar:")
            appendLine("""{"type":"final","text":"respuesta final"}""")
            appendLine("No inventes IDs: usa exactamente uno de la lista.")
        }

    private fun parseResponse(raw: String): ModelResponse {
        val jsonText = extractLastJsonObject(raw) ?: return ModelResponse(text = raw.trim())
        val root = runCatching { Json.parseToJsonElement(jsonText).jsonObject }.getOrNull()
            ?: return ModelResponse(text = raw.trim())

        return when (root["type"]?.jsonPrimitive?.content) {
            "tool" -> {
                val tool = root["tool"]?.jsonPrimitive?.content
                    ?: return ModelResponse(text = raw.trim())
                val arguments = (root["arguments"] as? JsonObject)
                    ?.mapValues { (_, value) -> value.asToolArgument() }
                    .orEmpty()
                ModelResponse(
                    text = root["text"]?.jsonPrimitive?.content.orEmpty(),
                    requestedTool = tool,
                    toolArguments = arguments
                )
            }
            "final" -> ModelResponse(text = root["text"]?.jsonPrimitive?.content.orEmpty())
            else -> ModelResponse(text = raw.trim())
        }
    }

    private fun JsonElement.asToolArgument(): String =
        if (this is JsonPrimitive && isString) content else toString()

    private fun extractLastJsonObject(text: String): String? {
        var end = text.lastIndexOf('}')
        while (end >= 0) {
            var depth = 0
            var inString = false
            var escaped = false
            for (index in end downTo 0) {
                val char = text[index]
                if (escaped) {
                    escaped = false
                    continue
                }
                if (char == '\\' && inString) {
                    escaped = true
                    continue
                }
                if (char == '"') {
                    inString = !inString
                    continue
                }
                if (inString) continue
                if (char == '}') depth++
                else if (char == '{') {
                    depth--
                    if (depth == 0) return text.substring(index, end + 1)
                }
            }
            end = text.lastIndexOf('}', end - 1)
        }
        return null
    }

    private fun runtimeEnvironment(executable: File): Map<String, String> {
        val bin = executable.parentFile ?: return emptyMap()
        val prefix = bin.parentFile ?: return emptyMap()
        val filesDir = prefix.parentFile ?: return emptyMap()

        return mapOf(
            "PREFIX" to prefix.absolutePath,
            "HOME" to File(filesDir, "home").apply { mkdirs() }.absolutePath,
            "TMPDIR" to File(prefix, "tmp").apply { mkdirs() }.absolutePath,
            "PATH" to (bin.absolutePath + ":" + System.getenv("PATH").orEmpty()),
            "LD_LIBRARY_PATH" to File(prefix, "lib").absolutePath,
            "LANG" to "C.UTF-8"
        )
    }
}
