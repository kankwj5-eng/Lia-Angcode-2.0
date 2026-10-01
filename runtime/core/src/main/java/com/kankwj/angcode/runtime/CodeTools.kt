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


data class CodeSymbol(
    val path: String,
    val line: Int,
    val kind: String,
    val name: String
)

class CodeSymbolsTool : AgentTool {
    override val id = "code.symbols"
    override val description = "Extrae un outline ligero de símbolos de código; Tree-sitter podrá reemplazar este backend."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"].orEmpty()
        val base = safeWorkspaceFile(context.workspace, path)
        if (!base.exists()) return ToolResponse(false, "Ruta no encontrada")

        val maxSymbols = call.arguments["maxSymbols"]?.toIntOrNull()?.coerceIn(1, 2_000) ?: 300
        val symbols = ArrayList<CodeSymbol>()
        val files = if (base.isFile) sequenceOf(base) else base.walkTopDown().asSequence().filter { it.isFile }

        for (file in files) {
            if (symbols.size >= maxSymbols) break
            if (file.length() > 1_000_000) continue
            val extension = file.extension.lowercase()
            if (extension !in setOf("kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "go", "rs", "c", "h", "cc", "cpp", "hpp")) continue

            val rel = file.relativeTo(context.workspace.canonicalFile).path
            runCatching {
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (symbols.size < maxSymbols) {
                            extractSymbol(extension, line)?.let { pair ->
                                symbols += CodeSymbol(rel, index + 1, pair.first, pair.second)
                            }
                        }
                    }
                }
            }
        }

        val output = symbols.joinToString("\n") {
            it.path + ":" + it.line + "\t" + it.kind + "\t" + it.name
        }
        return ToolResponse(true, output, mapOf("symbols" to symbols.size.toString(), "backend" to "fallback"))
    }

    private fun extractSymbol(extension: String, line: String): Pair<String, String>? {
        val trimmed = line.trim()
        val patterns = when (extension) {
            "kt", "kts" -> listOf(
                Regex("""^(?:public\s+|private\s+|protected\s+|internal\s+)?(?:data\s+|sealed\s+|open\s+|abstract\s+)?(class|interface|object|enum\s+class)\s+([A-Za-z_][A-Za-z0-9_]*)"""),
                Regex("""^(?:public\s+|private\s+|protected\s+|internal\s+)?(?:suspend\s+)?(fun)\s+(?:<[^>]+>\s*)?([A-Za-z_][A-Za-z0-9_]*)""")
            )
            "java" -> listOf(
                Regex("""^(?:public\s+|private\s+|protected\s+|static\s+|final\s+|abstract\s+)*(class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)""")
            )
            "py" -> listOf(
                Regex("""^(?:async\s+)?(def)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\("""),
                Regex("""^(class)\s+([A-Za-z_][A-Za-z0-9_]*)""")
            )
            "js", "jsx", "ts", "tsx" -> listOf(
                Regex("""^(?:export\s+)?(?:default\s+)?(class|function)\s+([A-Za-z_$][A-Za-z0-9_$]*)""")
            )
            "go" -> listOf(
                Regex("""^(func)\s+(?:\([^)]*\)\s*)?([A-Za-z_][A-Za-z0-9_]*)"""),
                Regex("""^(type)\s+([A-Za-z_][A-Za-z0-9_]*)\s+(?:struct|interface)""")
            )
            "rs" -> listOf(
                Regex("""^(?:pub\s+)?(fn|struct|enum|trait|impl)\s+([A-Za-z_][A-Za-z0-9_]*)""")
            )
            else -> listOf(
                Regex("""^(?:static\s+|inline\s+|extern\s+)*(?:[A-Za-z_][A-Za-z0-9_<>:*&\s]+\s+)([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
            )
        }

        for (pattern in patterns) {
            val match = pattern.find(trimmed) ?: continue
            return if (match.groupValues.size >= 3) {
                match.groupValues[1].replace(Regex("\\s+"), " ") to match.groupValues[2]
            } else {
                "function" to match.groupValues[1]
            }
        }
        return null
    }
}
