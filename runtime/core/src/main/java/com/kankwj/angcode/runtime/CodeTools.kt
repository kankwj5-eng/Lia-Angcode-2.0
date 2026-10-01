package com.kankwj.angcode.runtime

import java.io.File

internal fun safeWorkspaceFile(workspace: File, relativePath: String): File {
    require(!File(relativePath).isAbsolute) { "Solo se permiten rutas relativas al workspace" }
    val root = workspace.canonicalFile
    val target = File(root, relativePath).canonicalFile
    require(target.toPath().startsWith(root.toPath())) { "La ruta intenta salir del workspace" }
    return target
}

class CodeSearchTool : AgentTool {
    override val id = "code.search"
    override val description = "Busca texto recursivamente dentro del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val query = call.arguments["query"] ?: return ToolResponse(false, "Falta query")
        val base = safeWorkspaceFile(context.workspace, call.arguments["path"].orEmpty())
        if (!base.exists()) return ToolResponse(false, "Ruta no encontrada")

        val maxResults = call.arguments["maxResults"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
        val out = ArrayList<String>()
        var scanned = 0
        val files = if (base.isFile) sequenceOf(base) else base.walkTopDown().asSequence()

        for (file in files) {
            if (out.size >= maxResults || scanned >= 5_000) break
            if (!file.isFile || file.length() > 1_000_000) continue
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

class FilePatchTool : AgentTool {
    override val id = "file.patch"
    override val description = "Reemplaza una coincidencia exacta dentro de un archivo del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val old = call.arguments["old"] ?: return ToolResponse(false, "Falta old")
        val replacement = call.arguments["new"] ?: return ToolResponse(false, "Falta new")
        val target = safeWorkspaceFile(context.workspace, path)

        if (!target.isFile) return ToolResponse(false, "Archivo no encontrado: $path")
        if (target.length() > 2_000_000) return ToolResponse(false, "Archivo demasiado grande para file.patch")

        val source = target.readText()
        val first = source.indexOf(old)
        if (first < 0) return ToolResponse(false, "No se encontró el bloque exacto")
        val second = source.indexOf(old, first + old.length)
        if (second >= 0) return ToolResponse(false, "Hay varias coincidencias; usa un bloque más específico")

        target.writeText(source.replaceFirst(old, replacement))
        return ToolResponse(true, "Parche aplicado", mapOf("replacements" to "1"))
    }
}
