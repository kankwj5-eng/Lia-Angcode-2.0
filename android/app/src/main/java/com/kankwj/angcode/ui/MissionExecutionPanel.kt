package com.kankwj.angcode.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
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
import com.kankwj.angcode.agents.AdaptiveMissionCoordinator
import com.kankwj.angcode.agents.AgentRunConfig
import com.kankwj.angcode.agents.LlamaCliModelGateway
import com.kankwj.angcode.agents.MissionAnalysis
import com.kankwj.angcode.agents.MissionCapability
import com.kankwj.angcode.agents.MissionCoordinatorResult
import com.kankwj.angcode.agents.MissionStateMachine
import com.kankwj.angcode.agents.ResourceSnapshot
import com.kankwj.angcode.connectors.ConnectorSessionRegistry
import com.kankwj.angcode.connectors.shizuku.ShizukuBridgeManager
import com.kankwj.angcode.connectors.shizuku.registerShizukuTools
import com.kankwj.angcode.connectors.connectMcp
import com.kankwj.angcode.connectors.registerLightpandaTools
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
    var result by remember { mutableStateOf<MissionCoordinatorResult?>(null) }
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
                                    .registerLightpandaTools(context)

                                val browserClient = ConnectorSessionRegistry.get("browser")
                                if (browserClient != null) {
                                    broker.connectMcp(
                                        client = browserClient,
                                        namespace = "browser",
                                        permissions = setOf(
                                            ToolPermission.NETWORK,
                                            ToolPermission.PRIVATE_NETWORK,
                                            ToolPermission.MCP_EXTERNAL
                                        )
                                    )
                                }

                                val wantsAdvancedAndroid =
                                    MissionCapability.ANDROID_DEVICE in analysis.capabilities
                                val shizukuReady =
                                    wantsAdvancedAndroid &&
                                        ShizukuBridgeManager.status().serviceBound

                                if (shizukuReady) {
                                    broker.registerShizukuTools()
                                }

                                val permissions = mutableSetOf(
                                    ToolPermission.WORKSPACE_READ,
                                    ToolPermission.WORKSPACE_WRITE,
                                    ToolPermission.PROCESS_EXECUTE,
                                    ToolPermission.NETWORK,
                                    ToolPermission.ANDROID_BRIDGE
                                )
                                if (browserClient != null) {
                                    permissions += ToolPermission.PRIVATE_NETWORK
                                    permissions += ToolPermission.MCP_EXTERNAL
                                }
                                if (shizukuReady) {
                                    permissions += ToolPermission.SHIZUKU_PRIVILEGED
                                }

                                val toolContext = ToolContext(
                                    workspace = workspace,
                                    grantedPermissions = permissions,
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
                                val coordinator = AdaptiveMissionCoordinator(
                                    model = gateway,
                                    broker = broker
                                )
                                val session = MissionStateMachine().create(analysis)

                                coordinator.run(
                                    initial = session,
                                    rootContext = toolContext,
                                    resources = readResourceSnapshot(context),
                                    config = AgentRunConfig(
                                        maxSteps = 10,
                                        planningInterval = 4,
                                        memoryWindowChars = 20_000
                                    )
                                )
                            }
                        }

                        runResult.onSuccess { completedRun ->
                            result = completedRun
                            runCatching {
                                val workspace = projectStore.resolveActiveOrScratch()
                                MissionHistoryStore().save(
                                    workspace = workspace,
                                    analysis = analysis,
                                    result = completedRun,
                                    modelName = model.name
                                )
                            }
                        }.onFailure {
                            error = it.message ?: "Falló la ejecución local"
                        }
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
                                if (run.completed) "Misión completada" else "Misión detenida/incompleta",
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

private fun readResourceSnapshot(context: Context): ResourceSnapshot {
    val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)

    val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        .takeIf { it in 0..100 } ?: 50

    val batteryIntent = context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )
    val status = batteryIntent?.getIntExtra(
        BatteryManager.EXTRA_STATUS,
        BatteryManager.BATTERY_STATUS_UNKNOWN
    ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL

    val thermal = if (Build.VERSION.SDK_INT >= 29) {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        power.currentThermalStatus
    } else {
        0
    }

    return ResourceSnapshot(
        availableRamMb = (memory.availMem / (1024L * 1024L))
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt(),
        batteryPercent = batteryPercent,
        charging = charging,
        thermalLevel = thermal
    )
}

