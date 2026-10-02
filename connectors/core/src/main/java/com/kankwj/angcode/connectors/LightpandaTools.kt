package com.kankwj.angcode.connectors

import android.content.Context
import com.kankwj.angcode.runtime.AgentTool
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.ToolResponse

class BrowserRuntimeInstallTool(
    private val manager: LightpandaRuntimeManager
) : AgentTool {
    override val id = "browser_runtime.install"
    override val description = "Descarga Lightpanda oficial y verifica tamaño + SHA-256 antes de instalar."
    override val requiredPermissions = setOf(ToolPermission.NETWORK)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val result = manager.install()
        return ToolResponse(
            result.success,
            result.detail,
            mapOf(
                "path" to (result.path ?: ""),
                "sha256" to (result.sha256 ?: "")
            )
        )
    }
}

class BrowserRuntimeStartTool(
    private val manager: LightpandaRuntimeManager,
    private val broker: ToolBroker
) : AgentTool {
    override val id = "browser_runtime.start"
    override val description = "Prepara Debian PRoot, inicia Lightpanda MCP y registra browser.* dinámicamente."
    override val requiredPermissions = setOf(
        ToolPermission.NETWORK,
        ToolPermission.PRIVATE_NETWORK,
        ToolPermission.MCP_EXTERNAL,
        ToolPermission.PROCESS_EXECUTE,
        ToolPermission.SANDBOX_MANAGE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val port = call.arguments["port"]?.toIntOrNull()
            ?.takeIf { it in 1024..65535 }
            ?: LightpandaRuntimeManager.DEFAULT_PORT
        val sandbox = call.arguments["sandbox"]
            ?.takeIf { it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")) }
            ?: LightpandaRuntimeManager.DEFAULT_SANDBOX
        val image = call.arguments["image"]
            ?.takeIf { it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._/:@-]{0,200}$")) }
            ?: LightpandaRuntimeManager.DEFAULT_IMAGE

        val result = manager.startAndRegister(
            context = context,
            broker = broker,
            sandboxName = sandbox,
            image = image,
            port = port
        )

        return ToolResponse(
            result.success,
            result.detail,
            mapOf(
                "processId" to (result.processId ?: ""),
                "endpoint" to (result.endpoint ?: ""),
                "registeredTools" to result.registeredTools.toString(),
                "sandbox" to (result.sandboxName ?: "")
            )
        )
    }
}

class BrowserRuntimeStopTool(
    private val manager: LightpandaRuntimeManager,
    private val broker: ToolBroker
) : AgentTool {
    override val id = "browser_runtime.stop"
    override val description = "Cierra MCP/Lightpanda y elimina del broker las herramientas browser.* de esa sesión."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val ok = manager.stop(broker)
        return ToolResponse(ok, if (ok) "Browser detenido" else "No se pudo detener completamente")
    }
}

class BrowserRuntimeStatusTool(
    private val manager: LightpandaRuntimeManager
) : AgentTool {
    override val id = "browser_runtime.status"
    override val description = "Estado de instalación, proceso MCP y herramientas registradas de Lightpanda."
    override val requiredPermissions = emptySet<ToolPermission>()

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val status = manager.status()
        return ToolResponse(
            true,
            listOf(
                "installed=" + status.installed,
                "running=" + status.running,
                "processId=" + (status.processId ?: ""),
                "endpoint=" + (status.endpoint ?: ""),
                "registeredTools=" + status.registeredTools,
                "sandbox=" + (status.sandboxName ?: "")
            ).joinToString("\n")
        )
    }
}

fun ToolBroker.registerLightpandaTools(context: Context): ToolBroker = apply {
    val manager = LightpandaRuntimeManager(context.applicationContext)
    register(BrowserRuntimeInstallTool(manager))
    register(BrowserRuntimeStartTool(manager, this))
    register(BrowserRuntimeStopTool(manager, this))
    register(BrowserRuntimeStatusTool(manager))
}
