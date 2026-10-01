package com.kankwj.angcode.runtime

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

class WorkspaceManager(private val context: Context) {
    private val root = File(context.filesDir, "workspaces")

    init {
        root.mkdirs()
    }

    fun rootDirectory(): File = root

    fun listWorkspaces(includeSystem: Boolean = false): List<File> =
        root.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .filter { includeSystem || !it.name.startsWith("_") }
            .sortedByDescending { it.lastModified() }

    fun createWorkspace(name: String): File {
        val safe = sanitizeName(name)
        val dir = File(root, safe)
        dir.mkdirs()
        File(dir, "artifacts").mkdirs()
        File(dir, ".agent").mkdirs()
        return dir
    }

    fun importTree(treeUri: Uri, preferredName: String? = null): ImportResult {
        val source = DocumentFile.fromTreeUri(context, treeUri)
            ?: return ImportResult(false, null, 0, "No se pudo abrir la carpeta")

        val workspace = createWorkspace(preferredName ?: source.name ?: "proyecto")
        var copied = 0

        fun copyNode(node: DocumentFile, destination: File) {
            if (node.isDirectory) {
                destination.mkdirs()
                node.listFiles().forEach { child ->
                    val childName = sanitizeFileName(child.name ?: "archivo")
                    copyNode(child, File(destination, childName))
                }
                return
            }

            destination.parentFile?.mkdirs()
            context.contentResolver.openInputStream(node.uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
                copied++
            }
        }

        return runCatching {
            source.listFiles().forEach { child ->
                copyNode(child, File(workspace, sanitizeFileName(child.name ?: "archivo")))
            }
            ImportResult(true, workspace, copied, null)
        }.getOrElse {
            ImportResult(false, workspace, copied, it.message)
        }
    }

    fun isInsideWorkspace(workspace: File, candidate: File): Boolean {
        val workspacePath = workspace.canonicalFile.toPath()
        return candidate.canonicalFile.toPath().startsWith(workspacePath)
    }

    private fun sanitizeName(raw: String): String = sanitizeFileName(raw)
        .ifBlank { "workspace" }
        .take(80)

    private fun sanitizeFileName(raw: String): String = raw
        .replace(Regex("[\\/:*?\"<>|]"), "_")
        .replace("..", "_")
        .trim()
}

data class ImportResult(
    val success: Boolean,
    val workspace: File?,
    val copiedFiles: Int,
    val error: String?
)
