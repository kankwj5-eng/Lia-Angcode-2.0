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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.agents.AgentCancellationToken
import com.kankwj.angcode.agents.LlamaServerManager
import com.kankwj.angcode.agents.MissionAnalysis
import com.kankwj.angcode.agents.MissionCoordinatorResult
import com.kankwj.angcode.mission.BackgroundMissionSnapshot
import com.kankwj.angcode.mission.BackgroundMissionState
import com.kankwj.angcode.mission.MissionForegroundService
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import com.kankwj.angcode.ui.theme.PanelRaised
import com.kankwj.angcode.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MissionExecutionPanel(
    analysis: MissionAnalysis
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val executor = remember { LocalMissionExecutor(context) }

    var running by remember { mutableStateOf(false) }
    var cancellation by remember { mutableStateOf<AgentCancellationToken?>(null) }
    var result by remember { mutableStateOf<MissionCoordinatorResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var readiness by remember { mutableStateOf(executor.readiness()) }
    var backgroundState by remember {
        mutableStateOf(BackgroundMissionState.snapshot())
    }

    LaunchedEffect(Unit) {
        while (true) {
            backgroundState = BackgroundMissionState.snapshot()
            delay(1_000)
        }
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
                Icon(
                    if (readiness.ready) Icons.Rounded.Bolt else Icons.Rounded.Warning,
                    null,
                    tint = if (readiness.ready) AngOrange else Muted
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Ejecutor local",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            readiness.modelName == null -> readiness.detail
                            readiness.ready -> readiness.modelName + " · " + readiness.detail
                            else -> readiness.detail
                        },
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }

            Button(
                enabled = readiness.ready && !running,
                onClick = {
                    val cancellationToken = AgentCancellationToken()
                    cancellation = cancellationToken
                    running = true
                    result = null
                    error = null

                    scope.launch {
                        val runResult = withContext(Dispatchers.IO) {
                            runCatching {
                                executor.run(
                                    analysis = analysis,
                                    cancellation = cancellationToken
                                )
                            }
                        }

                        runResult.onSuccess { outcome ->
                            result = outcome.result
                            runCatching {
                                MissionHistoryStore().save(
                                    workspace = outcome.workspace,
                                    analysis = analysis,
                                    result = outcome.result,
                                    modelName = outcome.modelName
                                )
                            }
                        }.onFailure {
                            error = it.message ?: "Falló la ejecución local"
                        }

                        readiness = executor.readiness()
                        cancellation = null
                        running = false
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AngOrange,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Bolt, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (running) "Trabajando…" else "Ejecutar misión local",
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                enabled = readiness.ready && !running && !backgroundState.running,
                onClick = {
                    MissionForegroundService.start(context, analysis.mission)
                    backgroundState = BackgroundMissionSnapshot(
                        running = true,
                        mission = analysis.mission,
                        detail = "Iniciando misión en segundo plano",
                        startedAtMillis = System.currentTimeMillis()
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PanelRaised,
                    contentColor = InkWhite
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Bolt, null)
                Spacer(Modifier.width(8.dp))
                Text("Ejecutar en segundo plano", fontWeight = FontWeight.Bold)
            }

            if (backgroundState.running) {
                Surface(
                    color = Graphite,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            "Segundo plano activo",
                            color = AngOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            backgroundState.detail,
                            color = Muted,
                            fontSize = 10.sp
                        )
                        Button(
                            onClick = {
                                MissionForegroundService.cancel(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PanelRaised,
                                contentColor = InkWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Stop, null)
                            Spacer(Modifier.width(7.dp))
                            Text("Cancelar misión de fondo")
                        }
                    }
                }
            }

            if (running) {
                Button(
                    onClick = {
                        cancellation?.cancel()
                        LlamaServerManager.stop()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PanelRaised,
                        contentColor = InkWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancelar misión", fontWeight = FontWeight.Bold)
                }
            }

            if (!readiness.ready) {
                Text(
                    "La planificación funciona sin modelo. La ejecución necesita un GGUF activo y llama-server/llama-cli.",
                    color = Muted,
                    fontSize = 10.sp
                )
            }

            error?.let {
                Text(
                    "✕ " + it,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }

            result?.let { run ->
                Surface(
                    color = Graphite,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row {
                            Icon(
                                if (run.completed) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                null,
                                tint = if (run.completed) Success else AngOrange
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                when {
                                    run.cancelled -> "Misión cancelada"
                                    run.completed -> "Misión completada"
                                    else -> "Misión detenida/incompleta"
                                },
                                color = InkWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "Workers lógicos: " + run.taskResults.size +
                                " · inferencia compartida: 1×",
                            color = Muted,
                            fontSize = 10.sp
                        )

                        run.taskResults.forEach { task ->
                            Surface(
                                color = PanelRaised,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        task.role.name + " · " +
                                            if (task.completed) "OK" else "FALLO",
                                        color = if (task.completed) Success else AngOrange,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        task.answer.take(900),
                                        color = InkWhite,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                    Text(
                                        "pasos=" + task.stepsUsed,
                                        color = Muted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }

                        if (run.stalled) {
                            Text(
                                "El plan quedó bloqueado por dependencias o una tarea fallida.",
                                color = AngOrange,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
