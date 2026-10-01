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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
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
import com.kankwj.angcode.runtime.CheckpointManager
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import com.kankwj.angcode.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun CheckpointPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeStore = remember { ActiveProjectStore(context) }
    val manager = remember { CheckpointManager() }

    var refresh by remember { mutableIntStateOf(0) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val workspace = remember(refresh) { activeStore.active() }
    val checkpoints = remember(refresh, workspace) {
        workspace?.let(manager::list).orEmpty().take(8)
    }

    Surface(
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row {
                Icon(Icons.Rounded.History, null, tint = AngOrange)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Checkpoints",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Snapshots locales antes de cambios importantes",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            Button(
                enabled = workspace != null && !busy,
                onClick = {
                    val project = workspace ?: return@Button
                    busy = true
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                manager.create(project, "manual")
                            }
                        }
                        message = result.fold(
                            onSuccess = { "✓ " + it.name },
                            onFailure = { "✕ " + (it.message ?: "No se pudo crear") }
                        )
                        busy = false
                        refresh++
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AngOrange,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Save, null)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (busy) "Procesando…" else "Crear checkpoint",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            checkpoints.forEach { checkpoint ->
                val confirm = pendingRestore == checkpoint.name
                Surface(
                    color = Graphite,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(11.dp)
                    ) {
                        Icon(Icons.Rounded.Restore, null, tint = AngOrange)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                checkpoint.name,
                                color = InkWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                            Text(
                                DateFormat.getDateTimeInstance(
                                    DateFormat.SHORT,
                                    DateFormat.SHORT
                                ).format(Date(checkpoint.createdAt)) +
                                    " · " + checkpoint.bytes + " B",
                                color = Muted,
                                fontSize = 8.sp
                            )
                        }
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                if (!confirm) {
                                    pendingRestore = checkpoint.name
                                } else {
                                    val project = workspace ?: return@OutlinedButton
                                    busy = true
                                    scope.launch {
                                        val result = runCatching {
                                            withContext(Dispatchers.IO) {
                                                manager.restore(project, checkpoint.name)
                                            }
                                        }
                                        message = result.fold(
                                            onSuccess = { "✓ Restaurados " + it + " archivos" },
                                            onFailure = { "✕ " + (it.message ?: "No se pudo restaurar") }
                                        )
                                        pendingRestore = null
                                        busy = false
                                        refresh++
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (confirm) "Confirmar" else "Restaurar",
                                color = if (confirm) AngOrange else InkWhite,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            if (checkpoints.isEmpty()) {
                Text(
                    "No hay checkpoints manuales o automáticos todavía.",
                    color = Muted,
                    fontSize = 10.sp
                )
            }

            message?.let {
                Text(
                    it,
                    color = if (it.startsWith("✓")) Success else Muted,
                    fontSize = 9.sp
                )
            }
        }
    }
}
