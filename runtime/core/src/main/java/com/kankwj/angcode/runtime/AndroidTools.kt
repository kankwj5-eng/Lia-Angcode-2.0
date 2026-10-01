package com.kankwj.angcode.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build

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

fun ToolBroker.registerAndroidTools(context: Context): ToolBroker = apply {
    val app = context.applicationContext
    register(AndroidBatteryTool(app))
    register(ClipboardReadTool(app))
    register(ClipboardWriteTool(app))

    val toolPacks = ToolPackAssetRepository(app)
    register(ToolPackListTool(toolPacks))
    register(ToolPackInspectTool(toolPacks))
}
