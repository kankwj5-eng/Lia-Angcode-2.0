package com.kankwj.angcode.runtime

class CodeSearchTool : AgentTool {
    override val id = "code.search"
    override val description = "Busca texto en código del workspace con límites de archivos y resultados."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val query = call.arguments["query"] ?: return ToolResponse(false, "Falta query")
        val base = safePath(context.workspace, call.arguments["path"].orEmpty())
        if (!base.exists()) return ToolResponse(false, "Ruta no encontrada")

        val maxResults = call.arguments["maxResults"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
        val out = ArrayList<String>()
        var scanned = 0
        val files = if (base.isFile) sequenceOf(base) else base.walkTopDown().asSequence()

        for (file in files) {
            if (out.size >= maxResults || scanned >= 5_000) break
            if (!file.isFile || file.length() > 1_000_000) continue
            if (file.toPath().any { it.toString() == ".git" }) continue
            scanned++

            runCatching {
                file.bufferedReader().useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (out.size < maxResults && line.contains(query, ignoreCase = true)) {
                            val rel = file.relativeTo(context.workspace.canonicalFile).path
                            out += "$rel:${index + 1}:${line.take(300)}"
                        }
                    }
                }
            }
        }

        return ToolResponse(
            true,
            out.joinToString("\n"),
            mapOf("matches" to out.size.toString(), "filesScanned" to scanned.toString())
        )
    }
}
