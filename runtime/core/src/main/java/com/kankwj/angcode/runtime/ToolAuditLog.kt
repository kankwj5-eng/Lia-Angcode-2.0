package com.kankwj.angcode.runtime

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

object ToolAuditLog {
    private const val MAX_FILE_BYTES = 5L * 1024L * 1024L
    private const val MAX_VALUE_CHARS = 300
    private val lock = Any()

    private val sensitiveKeys = Regex(
        "(?i)(token|password|passwd|secret|authorization|cookie|api[_-]?key|" +
            "private[_-]?key|identity|content|text|code|prompt|stdin|args)"
    )

    fun record(
        workspace: File,
        call: ToolCall,
        requiredPermissions: Set<ToolPermission>,
        grantedPermissions: Set<ToolPermission>,
        response: ToolResponse,
        durationMs: Long
    ) {
        val root = workspace.canonicalFile
        if (!root.isDirectory) return

        val dir = File(root, ".agent").apply { mkdirs() }
        val file = File(dir, "tool-audit.jsonl")

        val event = buildJsonObject {
            put("ts", System.currentTimeMillis())
            put("tool", call.toolId)
            put("ok", response.ok)
            put("durationMs", durationMs)
            put("outputChars", response.output.length)
            put(
                "requiredPermissions",
                JsonPrimitive(requiredPermissions.map { it.name }.sorted().joinToString(","))
            )
            put(
                "grantedPermissions",
                JsonPrimitive(grantedPermissions.map { it.name }.sorted().joinToString(","))
            )
            put(
                "arguments",
                JsonObject(
                    call.arguments.mapValues { (key, value) ->
                        JsonPrimitive(sanitizeArgument(key, value))
                    }
                )
            )
            put(
                "metadata",
                JsonObject(
                    response.metadata.mapValues { (key, value) ->
                        JsonPrimitive(sanitizeArgument(key, value))
                    }
                )
            )
        }

        synchronized(lock) {
            rotateIfNeeded(file)
            file.appendText(event.toString() + "\n")
        }
    }

    fun file(workspace: File): File =
        File(workspace, ".agent/tool-audit.jsonl")

    fun tail(workspace: File, count: Int): List<String> {
        val file = file(workspace)
        if (!file.isFile) return emptyList()

        val limit = count.coerceIn(1, 500)
        val lines = ArrayDeque<String>(limit)

        file.bufferedReader().useLines { sequence ->
            sequence.forEach { line ->
                if (lines.size == limit) lines.removeFirst()
                lines.addLast(line)
            }
        }
        return lines.toList()
    }

    private fun sanitizeArgument(key: String, raw: String): String {
        if (sensitiveKeys.containsMatchIn(key)) return "<redacted>"
        val singleLine = raw
            .replace("\r", "\\r")
            .replace("\n", "\\n")
        return if (singleLine.length <= MAX_VALUE_CHARS) {
            singleLine
        } else {
            singleLine.take(MAX_VALUE_CHARS) + "…"
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.isFile || file.length() < MAX_FILE_BYTES) return
        val rotated = File(file.parentFile, "tool-audit.1.jsonl")
        rotated.delete()
        file.renameTo(rotated)
    }
}

class AuditTailTool : AgentTool {
    override val id = "audit.tail"
    override val description = "Devuelve los eventos recientes del Tool Broker en JSONL."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val count = call.arguments["count"]?.toIntOrNull()?.coerceIn(1, 500) ?: 50
        val events = ToolAuditLog.tail(context.workspace, count)
        return ToolResponse(
            true,
            events.joinToString("\n"),
            mapOf("count" to events.size.toString())
        )
    }
}
