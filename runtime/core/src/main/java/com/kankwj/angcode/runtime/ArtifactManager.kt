package com.kankwj.angcode.runtime

import android.content.Context
import android.net.Uri
import java.io.File

class ArtifactManager(private val context: Context) {
    fun artifactsDirectory(workspace: File): File =
        File(workspace, "artifacts").apply { mkdirs() }

    fun list(workspace: File): List<File> =
        artifactsDirectory(workspace)
            .listFiles()
            .orEmpty()
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }

    fun export(artifact: File, destination: Uri): Long {
        require(artifact.isFile) { "El artefacto no existe" }
        val out = context.contentResolver.openOutputStream(destination)
            ?: error("No se pudo abrir el destino")
        out.use { output ->
            artifact.inputStream().use { input ->
                return input.copyTo(output)
            }
        }
    }
}
