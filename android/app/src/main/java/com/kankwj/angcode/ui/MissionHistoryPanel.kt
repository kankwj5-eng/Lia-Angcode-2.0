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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
fun MissionHistoryPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { MissionHistoryStore() }
    val executor = remember { LocalMissionExecutor(context) }

    var refresh by remember { mutableIntStateOf(0) }
    var runningId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val workspace = remember(refresh) { ActiveProjectStore(context).active() }
    val entries = remember(refresh, workspace) {
        workspace?.let { store.list(it, 12) }.orEmpty()
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
                        "Historial",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        workspace?.name ?: "Sin proyecto activo",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            message?.let {
                Surface(
                    color = Graphite,
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        color = Muted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (entries.isEmpty()) {
                Text(
                    "Las ejecuciones terminadas aparecerán aquí.",
                    color = Muted,
                    fontSize = 11.sp
                )
            } else {
                entries.forEach { entry ->
                    Surface(
                        color = Graphite,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row {
                                Icon(
                                    if (entry.completed) {
                                        Icons.Rounded.CheckCircle
                                    } else {
                                        Icons.Rounded.Warning
                                    },
                                    null,
                                    tint = if (entry.completed) Success else AngOrange
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.mission.take(110),
                                        color = InkWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        entry.projectKind + " · " +
                                            entry.successfulTasks + "/" + entry.taskCount +
                                            " tareas · " +
                                            DateFormat.getDateTimeInstance(
                                                DateFormat.SHORT,
                                                DateFormat.SHORT
                                            ).format(Date(entry.createdAtMillis)),
                                        color = Muted,
                                        fontSize = 9.sp
                                    )
                                    if (entry.modelName.isNotBlank()) {
                                        Text(
                                            entry.modelName,
                                            color = Muted,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            if (!entry.completed) {
                                Button(
                                    enabled = runningId == null,
                                    onClick = {
                                        val readiness = executor.readiness()
                                        if (!readiness.ready) {
                                            message = readiness.detail
                                            return@Button
                                        }

                                        val session = store.loadSession(entry)
                                        if (session == null) {
                                            message = "No se pudo reconstruir esta misión."
                                            return@Button
                                        }

                                        runningId = entry.id
                                        message = "Reanudando misión…"

                                        scope.launch {
                                            val result = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    executor.run(
                                                        analysis = session.analysis,
                                                        initialSession = session
                                                    )
                                                }
                                            }

                                            result.onSuccess { outcome ->
                                                store.save(
                                                    workspace = outcome.workspace,
                                                    analysis = session.analysis,
                                                    result = outcome.result,
                                                    modelName = outcome.modelName
                                                )
                                                message = if (outcome.result.completed) {
                                                    "✓ Misión completada al reanudar."
                                                } else {
                                                    "La misión volvió a detenerse; el estado quedó guardado."
                                                }
                                            }.onFailure {
                                                message = "✕ " +
                                                    (it.message ?: "Falló la reanudación")
                                            }

                                            runningId = null
                                            refresh++
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AngOrange,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (runningId == entry.id) {
                                            "Reanudando…"
                                        } else {
                                            "Reanudar pendientes"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
