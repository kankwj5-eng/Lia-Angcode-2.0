package com.kankwj.angcode.runtime

import java.io.File
import java.security.MessageDigest

internal fun safeWorkspaceFile(workspace: File, relativePath: String): File {
    require(!File(relativePath).isAbsolute) { "Solo se permiten rutas relativas al workspace" }
    val root = workspace.canonicalFile
    val target = File(root, relativePath).canonicalFile
    require(target.toPath().startsWith(root.toPath())) { "La ruta intenta salir del workspace" }
    return target
}

internal fun safePath(workspace: File, relativePath: String): File =
    safeWorkspaceFile(workspace, relativePath)

class FilePatchTool : AgentTool {
    override val id = "file.patch"
    override val description = "Reemplaza una coincidencia exacta y única dentro de un archivo del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val find = call.arguments["find"] ?: call.arguments["old"]
            ?: return ToolResponse(false, "Falta find/old")
        val replace = call.arguments["replace"] ?: call.arguments["new"]
            ?: return ToolResponse(false, "Falta replace/new")
        if (find.isEmpty()) return ToolResponse(false, "El texto a buscar no puede estar vacío")

        val target = safeWorkspaceFile(context.workspace, path)
        if (!target.isFile) return ToolResponse(false, "Archivo no encontrado: $path")
        if (target.length() > 2_000_000) return ToolResponse(false, "Archivo demasiado grande para file.patch")

        val original = target.readText()
        val first = original.indexOf(find)
        if (first < 0) return ToolResponse(false, "No se encontró el texto exacto")
        val second = original.indexOf(find, first + find.length)
        if (second >= 0) return ToolResponse(false, "El texto aparece más de una vez; parche ambiguo")

        target.writeText(original.replaceFirst(find, replace))
        return ToolResponse(true, "Parche aplicado: $path")
    }
}

class FileHashTool : AgentTool {
    override val id = "file.sha256"
    override val description = "Calcula SHA-256 de un archivo del workspace."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val target = safeWorkspaceFile(context.workspace, path)
        if (!target.isFile) return ToolResponse(false, "Archivo no encontrado: $path")

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
