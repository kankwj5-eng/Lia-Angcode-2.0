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
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.agents.AgentRole
import com.kankwj.angcode.agents.MissionAnalysis
import com.kankwj.angcode.agents.MissionAnalyzer
import com.kankwj.angcode.runtime.ActiveProjectStore
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import com.kankwj.angcode.ui.theme.PanelRaised
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MissionPlannerCard(
    onAnalysis: (MissionAnalysis) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ActiveProjectStore(context) }
    var mission by remember { mutableStateOf("") }
    var analyzing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val active = remember(analyzing) { store.active() }

    Surface(
        color = Panel,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = PanelRaised,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        null,
                        tint = AngOrange,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Nueva misión",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        if (active != null) {
                            "Proyecto activo: " + active.name
                        } else {
                            "Sin proyecto activo · se usará _scratch"
                        },
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }

            OutlinedTextField(
                value = mission,
                onValueChange = {
                    mission = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 7,
                label = { Text("¿Qué quieres que haga AngCode?") },
                placeholder = {
                    Text(
                        "Ej.: corrige el error, ejecuta pruebas y compila el APK"
                    )
                }
            )

            Button(
                enabled = mission.isNotBlank() && !analyzing,
                onClick = {
                    val text = mission.trim()
                    if (text.isBlank()) return@Button
                    analyzing = true
                    error = null

                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val workspace = store.resolveActiveOrScratch()
                                MissionAnalyzer().analyze(text, workspace)
                            }
                        }

                        result.onSuccess(onAnalysis)
                            .onFailure {
                                error = it.message ?: "No se pudo analizar la misión"
                            }

                        analyzing = false
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AngOrange,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.ArrowForward, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (analyzing) "Analizando…" else "Analizar misión",
                    fontWeight = FontWeight.Bold
                )
            }

            error?.let {
                Text(
                    it,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun MissionPlanPreview(
    analysis: MissionAnalysis
) {
    Surface(
        color = Graphite,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null, tint = AngOrange)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Plan del Director",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        analysis.project.kind.name.replace('_', ' '),
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
                Text(
                    analysis.plan.tasks.size.toString() + " pasos",
                    color = AngOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            if (analysis.recommendedToolPacks.isNotEmpty()) {
                Text(
                    "Tool Packs · " + analysis.recommendedToolPacks.joinToString(" · "),
                    color = Muted,
                    fontSize = 11.sp
                )
            }

            analysis.plan.tasks.forEachIndexed { index, task ->
                Surface(
                    color = PanelRaised,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (index + 1).toString().padStart(2, '0'),
                            color = AngOrange,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                task.title,
                                color = InkWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                roleLabel(task.role) +
                                    if (task.dependsOn.isEmpty()) "" else
                                        " · depende de " + task.dependsOn.size,
                                color = Muted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun roleLabel(role: AgentRole): String =
    when (role) {
        AgentRole.DIRECTOR -> "Director"
        AgentRole.CODER -> "Coder"
        AgentRole.RESEARCHER -> "Research"
        AgentRole.TESTER -> "Tester"
        AgentRole.BUILDER -> "Builder"
        AgentRole.BROWSER -> "Browser"
    }
