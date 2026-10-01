package com.kankwj.angcode.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kankwj.angcode.runtime.InstalledRuntimeInspector
import com.kankwj.angcode.runtime.RuntimeBootstrapInstaller
import com.kankwj.angcode.runtime.RuntimeInstallResult
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RuntimeSetupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bootstrapUri by remember { mutableStateOf<Uri?>(null) }
    var bootstrapName by remember { mutableStateOf<String?>(null) }
    var expectedSha by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RuntimeInstallResult?>(null) }
    var status by remember {
        mutableStateOf(InstalledRuntimeInspector(context).inspect())
    }

    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            bootstrapUri = uri
            bootstrapName = uri.lastPathSegment?.substringAfterLast('/') ?: "bootstrap.zip"
            result = null
        }
    }

    val shaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            expectedSha = runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { reader ->
                        reader.readLine()
                            ?.trim()
                            ?.split(Regex("\\s+"))
                            ?.firstOrNull()
                            ?.lowercase()
                    }
            }.getOrNull()
            result = null
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Runtime AngCode",
                color = InkWhite,
                fontWeight = FontWeight.Bold
            )

            val statusText = if (status.installed) {
                "Instalado · " + status.executableCount + " ejecutables detectados"
            } else {
                "No instalado"
            }
            Text(statusText, color = if (status.installed) AngOrange else Muted)

            if (status.installed && status.executables.isNotEmpty()) {
                Text(
                    status.executables.keys.sorted().take(14).joinToString(" · "),
                    color = Muted
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { zipPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.weight(1f)
                ) {
                    androidx.compose.material3.Icon(Icons.Rounded.Folder, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (bootstrapUri == null) "Bootstrap" else "ZIP ✓")
                }

                OutlinedButton(
                    onClick = { shaPicker.launch(arrayOf("text/plain", "application/octet-stream")) },
                    modifier = Modifier.weight(1f)
                ) {
                    androidx.compose.material3.Icon(
                        if (expectedSha?.matches(Regex("^[0-9a-f]{64}$")) == true) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.Warning
                        },
                        null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("SHA-256")
                }
            }

            bootstrapName?.let {
                Text("Archivo: " + it, color = Muted)
            }

            Button(
                enabled = !installing &&
                    bootstrapUri != null &&
                    expectedSha?.matches(Regex("^[0-9a-f]{64}$")) == true,
                onClick = {
                    val uri = bootstrapUri ?: return@Button
                    val sha = expectedSha ?: return@Button

                    installing = true
                    result = null

                    scope.launch {
                        val installResult = withContext(Dispatchers.IO) {
                            val input = context.contentResolver.openInputStream(uri)
                                ?: error("No se pudo abrir el bootstrap")
                            RuntimeBootstrapInstaller(context).install(input, sha)
                        }
                        result = installResult
                        status = withContext(Dispatchers.IO) {
                            InstalledRuntimeInspector(context).inspect()
                        }
                        installing = false
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AngOrange,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(Icons.Rounded.Memory, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (installing) "Instalando runtime…" else "Verificar e instalar",
                    fontWeight = FontWeight.Bold
                )
            }

            result?.let { install ->
                Text(
                    if (install.success) {
                        "✓ " + install.detail + " · " +
                            install.filesExtracted + " archivos · " +
                            install.symlinksCreated + " symlinks"
                    } else {
                        "✕ " + install.detail
                    },
                    color = if (install.success) AngOrange else MaterialTheme.colorScheme.error
                )
            }

            Text(
                "El ZIP no toca el runtime existente hasta pasar SHA-256 y extraerse correctamente en staging.",
                color = Muted
            )
        }
    }
}

private fun persistReadPermission(
    context: android.content.Context,
    uri: Uri
) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}
