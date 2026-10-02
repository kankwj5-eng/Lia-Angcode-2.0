package com.kankwj.angcode.agents

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ModelToolProtocol {
    fun prompt(request: ModelRequest): String =
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

    fun parse(raw: String): ModelResponse {
        val jsonText = extractLastJsonObject(raw)
            ?: return ModelResponse(text = raw.trim())

        val root = runCatching {
            Json.parseToJsonElement(jsonText).jsonObject
        }.getOrNull() ?: return ModelResponse(text = raw.trim())

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
            "final" -> ModelResponse(
                text = root["text"]?.jsonPrimitive?.content.orEmpty()
            )
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

                if (char == '}') {
                    depth++
                } else if (char == '{') {
                    depth--
                    if (depth == 0) return text.substring(index, end + 1)
                }
            }

            end = text.lastIndexOf('}', end - 1)
        }
        return null
    }
}
