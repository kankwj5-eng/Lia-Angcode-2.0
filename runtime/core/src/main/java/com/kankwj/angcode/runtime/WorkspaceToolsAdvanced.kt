package com.kankwj.angcode.runtime

class WorkspaceSearchTool : AgentTool {
    override val id = "workspace.search"
    override val description = "Busca texto en archivos del workspace sin salir de la carpeta del proyecto."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val query = call.arguments["query"]?.takeIf { it.isNotEmpty() }
            ?: return ToolResponse(false, "Falta query")
        val path = call.arguments["path"].orEmpty()
        val maxResults = call.arguments["maxResults"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
        val maxFileBytes = call.arguments["maxFileBytes"]?.toLongOrNull()?.coerceIn(1, 2_000_000) ?: 500_000

        val root = safeWorkspaceFile(context.workspace, path)
        if (!root.exists()) return ToolResponse(false, "La ruta no existe: " + path)

        val matches = mutableListOf<String>()
        val files = if (root.isFile) {
            sequenceOf(root)
        } else {
            root.walkTopDown().asSequence().filter { it.isFile }
        }

        for (file in files) {
            if (matches.size >= maxResults) break
            if (file.length() > maxFileBytes) continue
            if (file.name.startsWith(".") && file.parentFile?.name == ".git") continue

            val text = runCatching { file.readText() }.getOrNull() ?: continue
            text.lineSequence().forEachIndexed { index, line ->
                if (matches.size < maxResults && line.contains(query, ignoreCase = true)) {
                    val relative = file.relativeTo(context.workspace.canonicalFile).path
                    matches += relative + ":" + (index + 1) + ":" + line.take(300)
                }
            }
        }

        return ToolResponse(
            ok = true,
            output = matches.joinToString("\n"),
            metadata = mapOf("matches" to matches.size.toString())
        )
    }
}
