package com.kankwj.angcode.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.kankwj.angcode.agents.AdaptiveMissionCoordinator
import com.kankwj.angcode.agents.AgentCancellationToken
import com.kankwj.angcode.agents.AgentRunConfig
import com.kankwj.angcode.agents.LlamaCliModelGateway
import com.kankwj.angcode.agents.LlamaServerManager
import com.kankwj.angcode.agents.LlamaServerModelGateway
import com.kankwj.angcode.agents.MissionAnalysis
import com.kankwj.angcode.agents.MissionCapability
import com.kankwj.angcode.agents.MissionCoordinatorResult
import com.kankwj.angcode.agents.MissionSession
import com.kankwj.angcode.agents.MissionStateMachine
import com.kankwj.angcode.agents.ResourceSnapshot
import com.kankwj.angcode.connectors.ConnectorSessionRegistry
import com.kankwj.angcode.connectors.GitHubTokenStore
import com.kankwj.angcode.connectors.registerGitHubTools
import com.kankwj.angcode.connectors.connectMcp
import com.kankwj.angcode.connectors.registerLightpandaTools
import com.kankwj.angcode.connectors.shizuku.ShizukuBridgeManager
import com.kankwj.angcode.connectors.shizuku.registerShizukuTools
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
import java.io.File

data class LocalMissionReadiness(
    val ready: Boolean,
    val modelName: String?,
    val detail: String
)

data class LocalMissionOutcome(
    val result: MissionCoordinatorResult,
    val modelName: String,
    val workspace: File
)

class LocalMissionExecutor(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val projectStore = ActiveProjectStore(appContext)
    private val modelStore = LocalModelStore(appContext)

    fun readiness(): LocalMissionReadiness {
        val model = modelStore.active()
            ?: return LocalMissionReadiness(false, null, "Selecciona un modelo GGUF en Ajustes")
        val executables = ExecutableDiscovery.forApp(appContext).asMap()
        if ("llama-server" !in executables && "llama-cli" !in executables) {
            return LocalMissionReadiness(false, model.name, "Instala el Tool Pack Local LLM")
        }
        val backend = if ("llama-server" in executables) "servidor persistente" else "fallback CLI"
        return LocalMissionReadiness(true, model.name, "Listo · " + backend)
    }

    fun run(
        analysis: MissionAnalysis,
        initialSession: MissionSession? = null,
        cancellation: AgentCancellationToken = AgentCancellationToken()
    ): LocalMissionOutcome {
        val model = modelStore.active()
            ?: error("No hay modelo GGUF activo")

        val executables = ExecutableDiscovery.forApp(appContext).asMap()
        val llamaServerPath = executables["llama-server"]
        val llamaCliPath = executables["llama-cli"]
        if (llamaServerPath == null && llamaCliPath == null) {
            error("llama-server/llama-cli no están instalados")
        }

        val workspace = projectStore.resolveActiveOrScratch()
        val broker = ToolBroker()
            .registerCoreTools()
            .registerAndroidTools(appContext)
            .registerModelTools(appContext)
            .registerLightpandaTools(appContext)

        val githubReady =
            MissionCapability.GITHUB_CONNECTOR in analysis.capabilities &&
                GitHubTokenStore(appContext).hasToken()

        if (githubReady) {
            broker.registerGitHubTools(appContext)
        }

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
            wantsAdvancedAndroid && ShizukuBridgeManager.status().serviceBound

        if (shizukuReady) {
            broker.registerShizukuTools()
        }

        val permissions = mutableSetOf(
            ToolPermission.WORKSPACE_READ,
            ToolPermission.WORKSPACE_WRITE,
            ToolPermission.PROCESS_EXECUTE,
            ToolPermission.NETWORK,
            ToolPermission.ANDROID_BRIDGE,
            ToolPermission.WORKTREE_MANAGE
        )

        if (browserClient != null) {
            permissions += ToolPermission.PRIVATE_NETWORK
            permissions += ToolPermission.MCP_EXTERNAL
        }
        if (githubReady) {
            permissions += ToolPermission.GITHUB_READ
        }
        if (shizukuReady) {
            permissions += ToolPermission.SHIZUKU_PRIVILEGED
        }

        val toolContext = ToolContext(
            workspace = workspace,
            grantedPermissions = permissions,
            executables = executables
        )

        broker.execute(
            ToolCall(
                "checkpoint.create",
                mapOf(
                    "label" to if (initialSession == null) {
                        "mission-start"
                    } else {
                        "mission-resume"
                    }
                )
            ),
            toolContext
        )

        val gateway = if (llamaServerPath != null) {
            runCatching {
                val handle = LlamaServerManager.ensureRunning(
                    executable = File(llamaServerPath),
                    model = File(model.path)
                )
                LlamaServerModelGateway(handle)
            }.getOrElse { error ->
                val fallback = llamaCliPath ?: throw error
                LlamaCliModelGateway(
                    executable = File(fallback),
                    model = File(model.path)
                )
            }
        } else {
            LlamaCliModelGateway(
                executable = File(llamaCliPath!!),
                model = File(model.path)
            )
        }
        val coordinator = AdaptiveMissionCoordinator(
            model = gateway,
            broker = broker
        )
        val stateMachine = MissionStateMachine()
        val session = initialSession?.let(stateMachine::prepareForResume)
            ?: stateMachine.create(analysis)

        val result = coordinator.run(
            initial = session,
            rootContext = toolContext,
            resources = readResourceSnapshot(appContext),
            config = AgentRunConfig(
                maxSteps = 10,
                planningInterval = 4,
                memoryWindowChars = 20_000
            ),
            cancellation = cancellation
        )

        return LocalMissionOutcome(
            result = result,
            modelName = model.name,
            workspace = workspace
        )
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
