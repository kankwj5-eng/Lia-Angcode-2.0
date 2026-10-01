package com.kankwj.angcode.runtime

import android.os.Build

class DeviceInfoTool : AgentTool {
    override val id = "android.device_info"
    override val description = "Devuelve datos técnicos no sensibles del dispositivo y ABI."
    override val requiredPermissions = setOf(ToolPermission.ANDROID_BRIDGE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val output = buildString {
            appendLine("manufacturer=${Build.MANUFACTURER}")
            appendLine("model=${Build.MODEL}")
            appendLine("device=${Build.DEVICE}")
            appendLine("android=${Build.VERSION.RELEASE}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("abis=${Build.SUPPORTED_ABIS.joinToString()}")
        }.trimEnd()
        return ToolResponse(true, output)
    }
}
