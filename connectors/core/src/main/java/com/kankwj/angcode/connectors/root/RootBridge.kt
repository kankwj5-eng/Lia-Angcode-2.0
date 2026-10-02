package com.kankwj.angcode.connectors.root

import android.content.Context
import com.kankwj.angcode.runtime.AgentTool
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.ToolResponse
import com.topjohnwu.superuser.Shell

data class RootBridgeStatus(
    val enabledByUser: Boolean,
    val shellCached: Boolean,
    val root: Boolean,
    val detail: String
)

class RootAccessStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "angcode-root",
        Context.MODE_PRIVATE
    )

    fun enabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
    }
}

object RootBridgeManager {
    fun status(context: Context): RootBridgeStatus {
        val enabled = RootAccessStore(context).enabled()
        val cached = Shell.getCachedShell()
        val root = enabled && cached?.isRoot == true
        return RootBridgeStatus(
            enabledByUser = enabled,
            shellCached = cached != null,
            root = root,
            detail = when {
                root -> "Root listo"
                enabled && cached == null -> "Root autorizado previamente; vuelve a comprobar para esta sesión"
                enabled -> "Shell presente sin root"
                else -> "Root desactivado"
            }
        )
    }

    fun requestRoot(context: Context): RootBridgeStatus {
        val store = RootAccessStore(context)
        val shell = runCatching { Shell.getShell() }.getOrNull()
        val granted = shell?.isRoot == true
        store.setEnabled(granted)
        return status(context)
    }

    fun disable(context: Context) {
        RootAccessStore(context).setEnabled(false)
        runCatching { Shell.getCachedShell()?.close() }
    }

    fun ready(context: Context): Boolean = status(context).root

    private fun run(command: String): ToolResponse {
        val shell = Shell.getCachedShell()
            ?: return ToolResponse(false, "Root no preparado; habilítalo en Ajustes")
        if (!shell.isRoot) {
            return ToolResponse(false, "La shell cacheada no tiene root")
        }

        return runCatching {
            val result = Shell.cmd(command).exec()
            ToolResponse(
                ok = result.isSuccess,
                output = buildString {
                    if (result.out.isNotEmpty()) append(result.out.joinToString("\n"))
                    if (result.err.isNotEmpty()) {
                        if (isNotEmpty()) append("\n--- stderr ---\n")
                        append(result.err.joinToString("\n"))
                    }
                },
                metadata = mapOf("exitCode" to result.code.toString())
            )
        }.getOrElse {
            ToolResponse(false, it.message ?: "Fallo ejecutando operación root")
        }
    }
}

private val ROOT_PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$")

class RootStatusTool(
    private val context: Context
) : AgentTool {
    override val id = "android.root.status"
    override val description = "Informa si AngCode tiene una shell root previamente autorizada."
    override val requiredPermissions = setOf(ToolPermission.ROOT_PRIVILEGED)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val status = RootBridgeManager.status(this.context)
        return ToolResponse(
            status.root,
            status.detail,
            mapOf(
                "enabledByUser" to status.enabledByUser.toString(),
                "shellCached" to status.shellCached.toString(),
                "root" to status.root.toString()
            )
        )
    }
}

class RootPackageInfoTool : AgentTool {
    override val id = "android.root.package_info"
    override val description = "Lee dumpsys package para un package Android validado mediante root."
    override val requiredPermissions = setOf(ToolPermission.ROOT_PRIVILEGED)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val packageName = call.arguments["package"]
            ?.takeIf { ROOT_PACKAGE.matches(it) }
            ?: return ToolResponse(false, "package faltante o inválido")
        return RootBridgeManager.run("dumpsys package " + packageName)
    }
}

class RootLogcatTool : AgentTool {
    override val id = "android.root.logcat"
    override val description = "Lee una ventana acotada de logcat mediante root."
    override val requiredPermissions = setOf(ToolPermission.ROOT_PRIVILEGED)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val lines = call.arguments["lines"]?.toIntOrNull()?.coerceIn(20, 3000) ?: 300
        return RootBridgeManager.run("logcat -d -t " + lines)
    }
}

class RootSettingsGetTool : AgentTool {
    override val id = "android.root.settings_get"
    override val description = "Lee una clave de settings Android mediante root."
    override val requiredPermissions = setOf(ToolPermission.ROOT_PRIVILEGED)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val namespace = call.arguments["namespace"]
            ?.takeIf { it in setOf("system", "secure", "global") }
            ?: return ToolResponse(false, "namespace inválido")
        val key = call.arguments["key"]
            ?.takeIf { it.matches(Regex("^[A-Za-z0-9_.:-]{1,160}$")) }
            ?: return ToolResponse(false, "key inválida")

        return RootBridgeManager.run(
            "settings get " + namespace + " " + key
        )
    }
}

fun ToolBroker.registerRootTools(context: Context): ToolBroker = apply {
    val app = context.applicationContext
    register(RootStatusTool(app))
    register(RootPackageInfoTool())
    register(RootLogcatTool())
    register(RootSettingsGetTool())
}
