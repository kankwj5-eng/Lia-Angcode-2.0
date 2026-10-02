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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.automation.AutomationRules
import com.kankwj.angcode.automation.AutomationScheduler
import com.kankwj.angcode.automation.AutomationStore
import com.kankwj.angcode.automation.LocalAutomation
import com.kankwj.angcode.runtime.ActiveProjectStore
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import com.kankwj.angcode.ui.theme.PanelRaised
import com.kankwj.angcode.ui.theme.Success
import java.text.DateFormat
import java.util.Date

@Composable
fun AutomationPanel() {
    val context = LocalContext.current
    val store = remember { AutomationStore(context) }
    val scheduler = remember { AutomationScheduler(context) }
    val projectStore = remember { ActiveProjectStore(context) }

    var refresh by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var mission by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("60") }
    var requireNetwork by remember { mutableStateOf(false) }
    var requireCharging by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val activeProject = remember(refresh) { projectStore.active() }
    val automations = remember(refresh) { store.list() }
    val intervalMinutes = interval.toLongOrNull()
    val canCreate =
        activeProject != null &&
            name.trim().isNotEmpty() &&
            mission.trim().isNotEmpty() &&
            intervalMinutes != null &&
            intervalMinutes in AutomationRules.MIN_INTERVAL_MINUTES..
                AutomationRules.MAX_INTERVAL_MINUTES

    Surface(
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, tint = AngOrange)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Automatizaciones locales",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        activeProject?.let { "Proyecto: " + it.name }
                            ?: "Activa un proyecto antes de programar una misión",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
                Text(
                    automations.size.toString(),
                    color = AngOrange,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(AutomationRules.MAX_NAME_CHARS) },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = mission,
                onValueChange = {
                    mission = it.take(AutomationRules.MAX_MISSION_CHARS)
                },
                label = { Text("Misión periódica") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = interval,
                onValueChange = { value ->
                    interval = value.filter(Char::isDigit).take(5)
                },
                label = { Text("Cada N minutos (mínimo 15)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ToggleRow(
                title = "Requiere red",
                detail = "WorkManager espera conectividad antes de ejecutar.",
                checked = requireNetwork,
                onCheckedChange = { requireNetwork = it }
            )
            ToggleRow(
                title = "Solo cargando",
                detail = "Útil para builds o misiones pesadas.",
                checked = requireCharging,
                onCheckedChange = { requireCharging = it }
            )

            Button(
                enabled = canCreate,
                onClick = {
                    val workspace = activeProject ?: return@Button
                    val minutes = intervalMinutes ?: return@Button

                    val definition = LocalAutomation(
                        id = AutomationRules.newId(),
                        name = name,
                        mission = mission,
                        workspaceName = workspace.name,
                        intervalMinutes = minutes,
                        requireNetwork = requireNetwork,
                        requireCharging = requireCharging
                    )

                    runCatching {
                        val saved = store.save(definition)
                        scheduler.schedule(saved)
                    }.onSuccess {
                        name = ""
                        mission = ""
                        interval = "60"
                        requireNetwork = false
                        requireCharging = false
                        message = "✓ Automatización programada"
                        refresh++
                    }.onFailure {
                        message = "✕ " + (it.message ?: "No se pudo programar")
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AngOrange,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(7.dp))
                Text("Programar misión", fontWeight = FontWeight.Bold)
            }

            message?.let {
                Text(
                    it,
                    color = if (it.startsWith("✓")) Success else AngOrange,
                    fontSize = 10.sp
                )
            }

            if (automations.isEmpty()) {
                Text(
                    "No hay misiones periódicas. El mínimo de Android WorkManager es 15 minutos.",
                    color = Muted,
                    fontSize = 10.sp
                )
            } else {
                automations.forEach { automation ->
                    AutomationRow(
                        automation = automation,
                        onRunNow = {
                            runCatching { scheduler.runNow(automation) }
                                .onSuccess {
                                    message = "✓ Ejecución solicitada"
                                }
                                .onFailure {
                                    message = "✕ " + (it.message ?: "Falló")
                                }
                        },
                        onToggle = { enabled ->
                            val updated = store.setEnabled(automation.id, enabled)
                            if (updated != null) {
                                if (enabled) {
                                    runCatching { scheduler.schedule(updated) }
                                        .onFailure {
                                            store.setEnabled(updated.id, false)
                                            message = "✕ " + (it.message ?: "Falló")
                                        }
                                } else {
                                    scheduler.cancelPeriodic(updated.id)
                                }
                                refresh++
                            }
                        },
                        onDelete = {
                            scheduler.cancelAll(automation.id)
                            store.remove(automation.id)
                            refresh++
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationRow(
    automation: LocalAutomation,
    onRunNow: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = Graphite,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        automation.name,
                        color = InkWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        automation.workspaceName + " · cada " +
                            automation.intervalMinutes + " min",
                        color = Muted,
                        fontSize = 9.sp
                    )
                }
                Switch(
                    checked = automation.enabled,
                    onCheckedChange = onToggle
                )
            }

            Text(
                automation.mission.take(260),
                color = InkWhite,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )

            val constraints = buildList {
                if (automation.requireNetwork) add("red")
                if (automation.requireCharging) add("cargando")
            }
            if (constraints.isNotEmpty()) {
                Text(
                    "Condiciones: " + constraints.joinToString(" · "),
                    color = Muted,
                    fontSize = 9.sp
                )
            }

            automation.lastStatus?.let { status ->
                Text(
                    "Último: " + status.uppercase() +
                        (automation.lastRunAtMillis?.let {
                            " · " + DateFormat.getDateTimeInstance(
                                DateFormat.SHORT,
                                DateFormat.SHORT
                            ).format(Date(it))
                        } ?: ""),
                    color = if (status == "success") Success else AngOrange,
                    fontSize = 9.sp
                )
            }
            automation.lastDetail?.let {
                Text(
                    it.take(320),
                    color = Muted,
                    fontSize = 9.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRunNow,
                    enabled = automation.enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Ahora", fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Delete, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Eliminar", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = PanelRaised,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = InkWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
                Text(detail, color = Muted, fontSize = 9.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
