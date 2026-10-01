package com.kankwj.angcode.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.app.ActivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

class AndroidBatteryTool(
    private val appContext: Context
) : AgentTool {
    override val id = "android.battery"
    override val description = "Consulta nivel y estado de carga mediante BatteryManager."
    override val requiredPermissions = setOf(ToolPermission.ANDROID_BRIDGE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val battery = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val chargeCounter = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentNow = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return ToolResponse(
            true,
            "level=" + level + "\nchargeCounterUaH=" + chargeCounter + "\ncurrentNowUa=" + currentNow,
            mapOf("level" to level.toString())
        )
    }
}

class ClipboardReadTool(
    private val appContext: Context
) : AgentTool {
    override val id = "android.clipboard.read"
    override val description = "Lee texto del portapapeles cuando Android lo permite."
    override val requiredPermissions = setOf(ToolPermission.CLIPBOARD_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ToolResponse(true, "")
        val item = clip.getItemAt(0)
        val text = item.coerceToText(appContext)?.toString().orEmpty()
        return ToolResponse(true, text)
    }
}

class ClipboardWriteTool(
    private val appContext: Context
) : AgentTool {
    override val id = "android.clipboard.write"
    override val description = "Escribe texto en el portapapeles."
    override val requiredPermissions = setOf(ToolPermission.CLIPBOARD_WRITE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val text = call.arguments["text"] ?: return ToolResponse(false, "Falta text")
        val label = call.arguments["label"] ?: "AngCode"
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        return ToolResponse(true, "Portapapeles actualizado")
    }
}

class AndroidMemoryTool(
    private val appContext: Context
) : AgentTool {
    override val id = "android.memory"
    override val description = "Consulta RAM disponible, total, umbral y estado low-memory."
    override val requiredPermissions = setOf(ToolPermission.ANDROID_BRIDGE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val output = listOf(
            "availableBytes=" + info.availMem,
            "totalBytes=" + info.totalMem,
            "thresholdBytes=" + info.threshold,
            "lowMemory=" + info.lowMemory
        ).joinToString("\n")
        return ToolResponse(
            true,
            output,
            mapOf(
                "availableMb" to (info.availMem / (1024L * 1024L)).toString(),
                "totalMb" to (info.totalMem / (1024L * 1024L)).toString()
            )
        )
    }
}

class AndroidStorageTool(
    private val appContext: Context
) : AgentTool {
    override val id = "android.storage"
    override val description = "Consulta espacio total y libre donde vive el runtime."
    override val requiredPermissions = setOf(ToolPermission.ANDROID_BRIDGE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val stat = StatFs(appContext.filesDir.absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        return ToolResponse(
            true,
            "totalBytes=" + total + "\navailableBytes=" + free,
            mapOf(
                "totalMb" to (total / (1024L * 1024L)).toString(),
                "availableMb" to (free / (1024L * 1024L)).toString()
            )
        )
    }
}

class AndroidThermalTool(
    private val appContext: Context
) : AgentTool {
    override val id = "android.thermal"
    override val description = "Consulta el estado térmico que Android expone a la aplicación."
    override val requiredPermissions = setOf(ToolPermission.ANDROID_BRIDGE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        if (Build.VERSION.SDK_INT < 29) {
            return ToolResponse(
                true,
                "unsupported",
                mapOf("thermalStatus" to "-1", "supported" to "false")
            )
        }
        val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val status = power.currentThermalStatus
        return ToolResponse(
            true,
            thermalLabel(status),
            mapOf("thermalStatus" to status.toString(), "supported" to "true")
        )
    }

    private fun thermalLabel(status: Int): String =
        when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "unknown"
        }
}

fun ToolBroker.registerAndroidTools(context: Context): ToolBroker = apply {
    val app = context.applicationContext
    register(AndroidBatteryTool(app))
    register(AndroidMemoryTool(app))
    register(AndroidStorageTool(app))
    register(AndroidThermalTool(app))
    register(ClipboardReadTool(app))
    register(ClipboardWriteTool(app))

    val toolPacks = ToolPackAssetRepository(app)
    register(ToolPackListTool(toolPacks))
    register(ToolPackInspectTool(toolPacks))
}
