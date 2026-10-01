package com.kankwj.angcode.runtime

import java.io.File
import java.security.MessageDigest

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

        val root = safePath(context.workspace, path)
        if (!root.exists()) return ToolResponse(false, "La ruta no existe: " + path)

        val matches = mutableListOf<String>()
        val files = if (root.isFile) sequenceOf(root) else root.walkTopDown().asSequence().filter { it.isFile }

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

class FilePatchTool : AgentTool {
    override val id = "file.patch"
    override val description = "Reemplaza una coincidencia exacta y única dentro de un archivo del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val find = call.arguments["find"] ?: return ToolResponse(false, "Falta find")
        val replace = call.arguments["replace"] ?: return ToolResponse(false, "Falta replace")
        if (find.isEmpty()) return ToolResponse(false, "find no puede estar vacío")

        val target = safePath(context.workspace, path)
        if (!target.isFile) return ToolResponse(false, "Archivo no encontrado: " + path)
        if (target.length() > 2_000_000) return ToolResponse(false, "Archivo demasiado grande para file.patch")

        val original = target.readText()
        var cursor = 0
        var count = 0
        while (true) {
            val index = original.indexOf(find, cursor)
            if (index < 0) break
            count++
            cursor = index + find.length
            if (count > 1) break
        }

        if (count == 0) return ToolResponse(false, "No se encontró el texto exacto")
        if (count > 1) return ToolResponse(false, "El texto aparece más de una vez; parche ambiguo")

        target.writeText(original.replaceFirst(find, replace))
        return ToolResponse(true, "Parche aplicado: " + path)
    }
}

class FileHashTool : AgentTool {
    override val id = "file.sha256"
    override val description = "Calcula SHA-256 de un archivo del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val target = safePath(context.workspace, path)
        if (!target.isFile) return ToolResponse(false, "Archivo no encontrado: " + path)

        val digest = MessageDigest.getInstance("SHA-256")
        target.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return ToolResponse(true, hex, mapOf("bytes" to target.length().toString()))
    }
}

internal fun safePath(workspace: File, relativePath: String): File {
    require(!File(relativePath).isAbsolute) { "Solo se permiten rutas relativas al workspace" }
    val root = workspace.canonicalFile
    val target = File(root, relativePath).canonicalFile
    require(target.toPath().startsWith(root.toPath())) { "La ruta intenta salir del workspace" }
    return target
}
