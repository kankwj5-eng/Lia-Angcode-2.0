package com.kankwj.angcode.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.runtime.ExecutableDiscovery
import com.kankwj.angcode.runtime.LocalModelStore
import com.kankwj.angcode.runtime.ModelImportResult
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import com.kankwj.angcode.ui.theme.PanelRaised
import com.kankwj.angcode.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ModelSetupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LocalModelStore(context) }
    var refresh by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ModelImportResult?>(null) }

    val models = remember(refresh) { store.list() }
    val executables = remember(refresh) { ExecutableDiscovery.forApp(context).asMap() }
    val llamaReady = "llama-cli" in executables

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null || importing) return@rememberLauncherForActivityResult

        importing = true
        importResult = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val name = queryDisplayName(context, uri) ?: "model.gguf"
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("No se pudo abrir el archivo")
                store.importGguf(input, name)
            }
            importResult = result
            importing = false
            refresh++
        }
    }

    Surface(
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row {
                Icon(Icons.Rounded.Memory, null, tint = AngOrange)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Modelo local",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (llamaReady) "llama-cli listo" else "Instala el pack Local LLM para inferencia",
                        color = if (llamaReady) Success else Muted,
                        fontSize = 11.sp
                    )
                }
            }

            OutlinedButton(
                enabled = !importing,
                onClick = {
                    picker.launch(arrayOf("application/octet-stream", "*/*"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(7.dp))
                Text(if (importing) "Importando y verificando…" else "Importar modelo GGUF")
            }

            importResult?.let { result ->
                Text(
                    (if (result.success) "✓ " else "✕ ") + result.detail,
                    color = if (result.success) Success
                    else androidx.compose.material3.MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }

            if (models.isEmpty()) {
                Text(
                    "No hay modelos importados. El archivo se copia al almacenamiento privado y se valida por cabecera GGUF + SHA-256.",
                    color = Muted,
                    fontSize = 11.sp
                )
            } else {
                models.forEach { model ->
                    Surface(
                        color = Graphite,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row {
                                Icon(
                                    if (model.active) Icons.Rounded.CheckCircle else Icons.Rounded.Memory,
                                    null,
                                    tint = if (model.active) Success else AngOrange
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        model.name,
                                        color = InkWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        humanBytes(model.bytes) +
                                            (if (model.active) " · ACTIVO" else ""),
                                        color = Muted,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            model.sha256?.let { sha ->
                                Text(
                                    "SHA-256 " + sha.take(16) + "…",
                                    color = Muted,
                                    fontSize = 9.sp
                                )
                            }

                            if (!model.active) {
                                Button(
                                    onClick = {
                                        store.activate(model.name)
                                        refresh++
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PanelRaised,
                                        contentColor = InkWhite
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Usar este modelo", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (!llamaReady) {
                Text(
                    "El modelo puede importarse ahora; la inferencia se habilitará cuando llama-cpp esté instalado en el runtime.",
                    color = Muted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun queryDisplayName(
    context: android.content.Context,
    uri: android.net.Uri
): String? {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column < 0) null else cursor.getString(column)
        }
    }.getOrNull()
}

private fun humanBytes(bytes: Long): String {
    val gb = 1024.0 * 1024.0 * 1024.0
    val mb = 1024.0 * 1024.0
    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        else -> bytes.toString() + " B"
    }
}
