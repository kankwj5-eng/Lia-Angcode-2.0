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
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.agents.AgentMemoryKind
import com.kankwj.angcode.agents.AgentRunConfig
import com.kankwj.angcode.agents.AgentRunResult
import com.kankwj.angcode.agents.LlamaCliModelGateway
import com.kankwj.angcode.agents.MissionAnalysis
import com.kankwj.angcode.agents.ToolCallingAgentEngine
import com.kankwj.angcode.runtime.ActiveProjectStore
import com.kankwj.angcode.runtime.ExecutableDiscovery
import com.kankwj.angcode.runtime.LocalModelStore
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.registerAndroidTools
import com.kankwj.angcode.runtime.registerCoreTools
import com.kankwj.angcode.runtime.registerModelTools
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
fun MissionExecutionPanel(
    analysis: MissionAnalysis
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val projectStore = remember { ActiveProjectStore(context) }
    val modelStore = remember { LocalModelStore(context) }

    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AgentRunResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val activeModel = modelStore.active()
    val executables = ExecutableDiscovery.forApp(context).asMap()
    val llama = executables["llama-cli"]
    val ready = activeModel != null && llama != null

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
                    if (ready) Icons.Rounded.Bolt else Icons.Rounded.Warning,
                    null,
                    tint = if (ready) AngOrange else Muted
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
                            activeModel == null -> "Selecciona un modelo GGUF en Ajustes"
                            llama == null -> "Instala el Tool Pack Local LLM"
                            else -> activeModel.name
                        },
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }

            Button(
                enabled = ready && !running,
                onClick = {
                    val model = activeModel ?: return@Button
                    val llamaPath = llama ?: return@Button
                    running = true
                    result = null
                    error = null

                    scope.launch {
                        val runResult = runCatching {
                            withContext(Dispatchers.IO) {
                                val workspace = projectStore.resolveActiveOrScratch()
                                val broker = ToolBroker()
                                    .registerCoreTools()
                                    .registerAndroidTools(context)
                                    .registerModelTools(context)

                                val toolContext = ToolContext(
                                    workspace = workspace,
                                    grantedPermissions = setOf(
                                        ToolPermission.WORKSPACE_READ,
                                        ToolPermission.WORKSPACE_WRITE,
                                        ToolPermission.PROCESS_EXECUTE,
                                        ToolPermission.NETWORK,
                                        ToolPermission.ANDROID_BRIDGE
                                    ),
                                    executables = ExecutableDiscovery.forApp(context).asMap()
                                )

                                // Every run starts with a rollback point.
                                broker.execute(
                                    ToolCall(
                                        "checkpoint.create",
                                        mapOf("label" to "mission-start")
                                    ),
                                    toolContext
                                )

                                val gateway = LlamaCliModelGateway(
                                    executable = File(llamaPath),
                                    model = File(model.path)
                                )
                                val engine = ToolCallingAgentEngine(
                                    model = gateway,
                                    broker = broker
                                )

                                engine.run(
                                    task = missionPrompt(analysis),
                                    context = toolContext,
                                    config = AgentRunConfig(
                                        maxSteps = 18,
                                        planningInterval = 4,
                                        memoryWindowChars = 24_000
                                    )
                                )
                            }
                        }

                        runResult.onSuccess { result = it }
                            .onFailure { error = it.message ?: "Falló la ejecución local" }
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

            if (!ready) {
                Text(
                    "La misión ya puede planificarse sin modelo. La ejecución se activa cuando existen GGUF activo + llama-cli.",
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
                                if (run.completed) "Misión completada" else "Misión incompleta",
                                color = InkWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "Pasos de agente: " + run.stepsUsed,
                            color = Muted,
                            fontSize = 10.sp
                        )

                        Text(
                            run.answer,
                            color = InkWhite,
                            fontSize = 12.sp
                        )

                        val activity = run.memory
                            .filter {
                                it.kind == AgentMemoryKind.TOOL_CALL ||
                                    it.kind == AgentMemoryKind.OBSERVATION ||
                                    it.kind == AgentMemoryKind.ERROR
                            }
                            .takeLast(6)

                        if (activity.isNotEmpty()) {
                            Text(
                                activity.joinToString("\n") { item ->
                                    "[" + item.kind.name + "] " +
                                        (item.toolId?.let { it + " · " } ?: "") +
                                        item.text.take(180)
                                },
                                color = Muted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun missionPrompt(analysis: MissionAnalysis): String =
    buildString {
        appendLine("MISIÓN")
        appendLine(analysis.mission)
        appendLine()
        appendLine("TIPO DE PROYECTO")
        appendLine(analysis.project.kind.name)
        appendLine()
        appendLine("TOOL PACKS RECOMENDADOS")
        appendLine(analysis.recommendedToolPacks.joinToString(", "))
        appendLine()
        appendLine("PLAN PROPUESTO")
        analysis.plan.tasks.forEachIndexed { index, task ->
            appendLine(
                (index + 1).toString() + ". " +
                    task.role.name + ": " + task.title
            )
        }
        appendLine()
        appendLine("Trabaja sobre el workspace actual. Inspecciona antes de editar. " +
            "Usa herramientas estructuradas. Después de cambios, verifica con pruebas/build cuando corresponda. " +
            "No declares éxito sin evidencia.")
    }
