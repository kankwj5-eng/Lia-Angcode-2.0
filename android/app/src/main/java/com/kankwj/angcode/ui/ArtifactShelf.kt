package com.kankwj.angcode.ui

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
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
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
import com.kankwj.angcode.runtime.ActiveProjectStore
import com.kankwj.angcode.runtime.ArtifactManager
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.registerCoreTools
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
import java.io.File

@Composable
fun ArtifactShelf() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeStore = remember { ActiveProjectStore(context) }
    val manager = remember { ArtifactManager(context) }

    var refresh by remember { mutableIntStateOf(0) }
    var pendingExport by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val workspace = remember(refresh) { activeStore.active() }
    val artifacts = remember(refresh, workspace) {
        workspace?.let(manager::list).orEmpty()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val artifact = pendingExport
        if (uri != null && artifact != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        manager.export(artifact, uri)
                    }
                }
                message = result.fold(
                    onSuccess = { "✓ Exportados " + humanArtifactBytes(it) },
                    onFailure = { "✕ " + (it.message ?: "No se pudo exportar") }
                )
            }
        }
        pendingExport = null
    }

    Surface(
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row {
                Icon(Icons.Rounded.Archive, null, tint = AngOrange)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Artefactos",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        workspace?.let { "Proyecto: " + it.name }
                            ?: "Importa o activa un proyecto",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
                Text(
                    artifacts.size.toString(),
                    color = AngOrange,
                    fontWeight = FontWeight.Bold
                )
            }

            if (workspace != null) {
                Button(
                    onClick = {
                        scope.launch {
                            val response = withContext(Dispatchers.IO) {
                                ToolBroker()
                                    .registerCoreTools()
                                    .execute(
                                        ToolCall(
                                            "artifact.zip",
                                            mapOf(
                                                "path" to "",
                                                "name" to (workspace.name + "-export")
                                            )
                                        ),
                                        ToolContext(
                                            workspace = workspace,
                                            grantedPermissions = setOf(
                                                ToolPermission.WORKSPACE_READ,
                                                ToolPermission.WORKSPACE_WRITE
                                            )
                                        )
                                    )
                            }
                            message = if (response.ok) {
                                "✓ Creado " + response.output
                            } else {
                                "✕ " + response.output
                            }
                            refresh++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngOrange,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Archive, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Empaquetar proyecto", fontWeight = FontWeight.Bold)
                }
            }

            if (artifacts.isEmpty()) {
                Text(
                    "Todavía no hay APK, ZIP, logs u otros resultados en artifacts/.",
                    color = Muted,
                    fontSize = 11.sp
                )
            } else {
                artifacts.forEach { artifact ->
                    Surface(
                        color = Graphite,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = AngOrange)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    artifact.name,
                                    color = InkWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    humanArtifactBytes(artifact.length()),
                                    color = Muted,
                                    fontSize = 9.sp
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    pendingExport = artifact
                                    exportLauncher.launch(artifact.name)
                                }
                            ) {
                                Icon(Icons.Rounded.Download, null)
                                Spacer(Modifier.width(5.dp))
                                Text("Exportar", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            message?.let {
                Row {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        null,
                        tint = if (it.startsWith("✓")) Success else AngOrange
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(it, color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

private fun humanArtifactBytes(bytes: Long): String {
    val mb = 1024.0 * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        else -> bytes.toString() + " B"
    }
}
