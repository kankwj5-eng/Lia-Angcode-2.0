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
import com.kankwj.angcode.agents.ModelRuntimeProfileSelector
import com.kankwj.angcode.agents.ResourceSnapshot
import com.kankwj.angcode.connectors.ConnectorSessionRegistry
import com.kankwj.angcode.connectors.GitHubTokenStore
import com.kankwj.angcode.connectors.registerGitHubTools
import com.kankwj.angcode.connectors.connectMcp
import com.kankwj.angcode.connectors.registerLightpandaTools
import com.kankwj.angcode.connectors.root.RootBridgeManager
import com.kankwj.angcode.connectors.root.registerRootTools
import com.kankwj.angcode.connectors.shizuku.ShizukuBridgeManager
import com.kankwj.angcode.connectors.shizuku.registerShizukuTools
import com.kankwj.angcode.runtime.ActiveProjectStore
import com.kankwj.angcode.runtime.ExecutableDiscovery
import com.kankwj.angcode.runtime.LocalModelStore
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.WorkspaceManager
import com.kankwj.angcode.runtime.registerAndroidTools
import com.kankwj.angcode.runtime.registerCoreTools
import com.kankwj.angcode.runtime.registerModelTools
import java.io.File

data class LocalMissionReadiness(
    val ready: Boolean,
    val modelName: String?,
    val detail: String
)

data class MissionPrivilegeOption(
    val id: String,
    val title: String,
    val detail: String,
    val permissions: Set<ToolPermission>,
    val available: Boolean
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

    private fun validatedWorkspace(candidate: File): File {
        val manager = WorkspaceManager(appContext)
        val root = manager.rootDirectory().canonicalFile
        val target = candidate.canonicalFile
        require(target.isDirectory) { "Workspace de automatización no encontrado" }
        require(target.toPath().startsWith(root.toPath())) {
            "Workspace de automatización fuera de files/workspaces"
        }
        require(target != root) { "La raíz de workspaces no es un proyecto" }
        return target
    }

    fun privilegeOptions(analysis: MissionAnalysis): List<MissionPrivilegeOption> {
        val executables = ExecutableDiscovery.forApp(appContext).asMap()
        val advancedAndroid = MissionCapability.ANDROID_DEVICE in analysis.capabilities
        val shizuku = ShizukuBridgeManager.status()
        val githubReady =
            MissionCapability.GITHUB_CONNECTOR in analysis.capabilities &&
                GitHubTokenStore(appContext).hasToken()
        val rootReady =
            advancedAndroid &&
                RootBridgeManager.ready(appContext)

        return listOf(
            MissionPrivilegeOption(
                id = "clipboard",
                title = "Portapapeles",
                detail = "Permite leer y escribir el portapapeles durante esta misión.",
                permissions = setOf(
                    ToolPermission.CLIPBOARD_READ,
                    ToolPermission.CLIPBOARD_WRITE
                ),
                available = true
            ),
            MissionPrivilegeOption(
                id = "android-ui",
                title = "Acciones visibles Android",
                detail = "Permite abrir apps, URLs y selectores visibles.",
                permissions = setOf(ToolPermission.ANDROID_UI_ACTION),
                available = advancedAndroid
            ),
            MissionPrivilegeOption(
                id = "shizuku",
                title = "Shizuku avanzado",
                detail = "Permite logcat, inspección, captura e instalación APK mediante el servicio autorizado.",
                permissions = setOf(ToolPermission.SHIZUKU_PRIVILEGED),
                available = advancedAndroid && shizuku.serviceBound
            ),
            MissionPrivilegeOption(
                id = "adb",
                title = "ADB remoto",
                detail = "Permite conectar/usar dispositivos ADB y red privada para ADB.",
                permissions = setOf(
                    ToolPermission.ADB_REMOTE,
                    ToolPermission.PRIVATE_NETWORK
                ),
                available = "adb" in executables
            ),
            MissionPrivilegeOption(
                id = "github-write",
                title = "GitHub escritura",
                detail = "Permite crear ramas, crear/actualizar archivos con SHA esperado y abrir PRs.",
                permissions = setOf(ToolPermission.GITHUB_WRITE),
                available = githubReady
            ),
            MissionPrivilegeOption(
                id = "root",
                title = "Root avanzado",
                detail = "Permite herramientas root limitadas: package info, logcat y lectura de settings.",
                permissions = setOf(ToolPermission.ROOT_PRIVILEGED),
                available = rootReady
            ),
            MissionPrivilegeOption(
                id = "ssh",
                title = "SSH remoto",
                detail = "Permite delegar tareas a hosts con known_hosts verificado.",
                permissions = setOf(ToolPermission.SSH_REMOTE),
                available = "ssh" in executables
            )
        )
    }

    fun run(
        analysis: MissionAnalysis,
        initialSession: MissionSession? = null,
        cancellation: AgentCancellationToken = AgentCancellationToken(),
        approvedPermissions: Set<ToolPermission> = emptySet(),
        workspaceOverride: File? = null
    ): LocalMissionOutcome {
        val model = modelStore.active()
            ?: error("No hay modelo GGUF activo")

        val executables = ExecutableDiscovery.forApp(appContext).asMap()
        val llamaServerPath = executables["llama-server"]
        val llamaCliPath = executables["llama-cli"]
        if (llamaServerPath == null && llamaCliPath == null) {
            error("llama-server/llama-cli no están instalados")
        }

        val workspace = workspaceOverride?.let(::validatedWorkspace)
            ?: projectStore.resolveActiveOrScratch()
        val resources = readResourceSnapshot(appContext)
        val modelProfile = ModelRuntimeProfileSelector.select(resources)

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
        val allowedElevated = setOf(
            ToolPermission.CLIPBOARD_READ,
            ToolPermission.CLIPBOARD_WRITE,
            ToolPermission.ANDROID_UI_ACTION,
            ToolPermission.SHIZUKU_PRIVILEGED,
            ToolPermission.ROOT_PRIVILEGED,
            ToolPermission.ADB_REMOTE,
            ToolPermission.SSH_REMOTE,
            ToolPermission.GITHUB_WRITE,
            ToolPermission.PRIVATE_NETWORK
        )
        val approved = approvedPermissions.intersect(allowedElevated)

        val shizukuReady =
            wantsAdvancedAndroid &&
                ToolPermission.SHIZUKU_PRIVILEGED in approved &&
                ShizukuBridgeManager.status().serviceBound

        if (shizukuReady) {
            broker.registerShizukuTools()
        }

        val rootMissionReady =
            wantsAdvancedAndroid &&
                ToolPermission.ROOT_PRIVILEGED in approved &&
                RootBridgeManager.ready(appContext)

        if (rootMissionReady) {
            broker.registerRootTools(appContext)
        }

        val permissions = mutableSetOf(
            ToolPermission.WORKSPACE_READ,
            ToolPermission.WORKSPACE_WRITE,
            ToolPermission.ARTIFACT_WRITE,
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
            if (ToolPermission.GITHUB_WRITE in approved) {
                permissions += ToolPermission.GITHUB_WRITE
            }
        }
        if (shizukuReady) {
            permissions += ToolPermission.SHIZUKU_PRIVILEGED
        }
        if (rootMissionReady) {
            permissions += ToolPermission.ROOT_PRIVILEGED
        }
        if (ToolPermission.CLIPBOARD_READ in approved) {
            permissions += ToolPermission.CLIPBOARD_READ
        }
        if (ToolPermission.CLIPBOARD_WRITE in approved) {
            permissions += ToolPermission.CLIPBOARD_WRITE
        }
        if (ToolPermission.ANDROID_UI_ACTION in approved && wantsAdvancedAndroid) {
            permissions += ToolPermission.ANDROID_UI_ACTION
        }
        if (ToolPermission.ADB_REMOTE in approved && "adb" in executables) {
            permissions += ToolPermission.ADB_REMOTE
            permissions += ToolPermission.PRIVATE_NETWORK
        }
        if (ToolPermission.SSH_REMOTE in approved && "ssh" in executables) {
            permissions += ToolPermission.SSH_REMOTE
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
                    model = File(model.path),
                    contextSize = modelProfile.contextSize,
                    threads = modelProfile.threads,
                    batchSize = modelProfile.batchSize
                )
                LlamaServerModelGateway(handle)
            }.getOrElse { error ->
                val fallback = llamaCliPath ?: throw error
                LlamaCliModelGateway(
                    executable = File(fallback),
                    model = File(model.path),
                    contextSize = modelProfile.contextSize,
                    threads = modelProfile.threads,
                    batchSize = modelProfile.batchSize
                )
            }
        } else {
            LlamaCliModelGateway(
                executable = File(llamaCliPath!!),
                model = File(model.path),
                contextSize = modelProfile.contextSize,
                threads = modelProfile.threads,
                batchSize = modelProfile.batchSize
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
            resources = resources,
            config = AgentRunConfig(
                maxSteps = modelProfile.maxAgentSteps,
                planningInterval = 4,
                memoryWindowChars = modelProfile.memoryWindowChars
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
