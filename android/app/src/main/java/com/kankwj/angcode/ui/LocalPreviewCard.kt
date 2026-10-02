package com.kankwj.angcode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.WorkspaceManager
import com.kankwj.angcode.runtime.registerCoreTools
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PreviewRow(
    val port: Int,
    val url: String,
    val status: String,
    val title: String
)

@Composable
fun LocalPreviewCard(
    onOpen: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val broker = remember { ToolBroker().registerCoreTools() }
    val workspace = remember {
        WorkspaceManager(context).createWorkspace("_preview")
    }
    val toolContext = remember {
        ToolContext(
            workspace = workspace,
            grantedPermissions = setOf(ToolPermission.PRIVATE_NETWORK)
        )
    }

    var scanning by remember { mutableStateOf(false) }
    var previews by remember { mutableStateOf<List<PreviewRow>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    Surface(
        color = Graphite,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row {
                Icon(Icons.Rounded.Visibility, null, tint = AngOrange)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Previews locales",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        if (previews.isEmpty()) {
                            "Vite · Next · Python · servidores localhost"
                        } else {
                            previews.size.toString() + " servidor(es) detectados"
                        },
                        color = Muted,
                        fontSize = 9.sp
                    )
                }

                OutlinedButton(
                    enabled = !scanning,
                    onClick = {
                        scanning = true
                        message = null
                        scope.launch {
                            val response = withContext(Dispatchers.IO) {
                                broker.execute(
                                    ToolCall("preview.scan"),
                                    toolContext
                                )
                            }

                            if (response.ok) {
                                previews = parsePreviewRows(response.output)
                                message = if (previews.isEmpty()) {
                                    "No hay previews escuchando en puertos comunes."
                                } else {
                                    null
                                }
                            } else {
                                message = response.output
                            }
                            scanning = false
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (scanning) "Buscando…" else "Buscar",
                        fontSize = 9.sp
                    )
                }
            }

            previews.forEach { preview ->
                Surface(
                    color = Color(0xFF17171B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                preview.title.ifBlank {
                                    "localhost:" + preview.port
                                },
                                color = InkWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                            Text(
                                preview.url +
                                    if (preview.status == "-") "" else
                                        " · HTTP " + preview.status,
                                color = Muted,
                                fontSize = 9.sp
                            )
                        }

                        Button(
                            onClick = { onOpen(preview.url) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AngOrange,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Icon(Icons.Rounded.OpenInBrowser, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Abrir", fontSize = 9.sp)
                        }
                    }
                }
            }

            message?.let {
                Text(
                    it,
                    color = Muted,
                    fontSize = 9.sp
                )
            }
        }
    }
}

private fun parsePreviewRows(output: String): List<PreviewRow> =
    output.lineSequence()
        .mapNotNull { line ->
            val parts = line.split('\t', limit = 4)
            if (parts.size < 2) return@mapNotNull null
            val port = parts[0].toIntOrNull()
                ?: return@mapNotNull null
            PreviewRow(
                port = port,
                url = parts[1],
                status = parts.getOrElse(2) { "-" },
                title = parts.getOrElse(3) { "" }
            )
        }
        .toList()
