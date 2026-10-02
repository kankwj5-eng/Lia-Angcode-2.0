package com.kankwj.angcode.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.hardware.Sensor
import android.hardware.SensorManager
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

class AndroidNetworkTool(
    private val appContext: Context
) : AgentTool {
    override val id = "android.network"
    override val description = "Consulta conectividad activa y transportes disponibles sin leer tráfico."
    override val requiredPermissions = setOf(ToolPermission.ANDROID_BRIDGE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
            ?: return ToolResponse(true, "connected=false", mapOf("connected" to "false"))
        val caps = manager.getNetworkCapabilities(network)
            ?: return ToolResponse(true, "connected=false", mapOf("connected" to "false"))

        val transports = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
        }

        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        return ToolResponse(
            true,
            listOf(
                "connected=true",
                "validated=" + validated,
                "metered=" + metered,
                "transports=" + transports.joinToString(",")
            ).joinToString("\n"),
            mapOf(
                "connected" to "true",
                "validated" to validated.toString(),
                "metered" to metered.toString(),
                "transports" to transports.joinToString(",")
            )
        )
    }
}

class AndroidSensorsListTool(
    private val appContext: Context
) : AgentTool {
    override val id = "android.sensors.list"
    override val description = "Lista sensores físicos disponibles en el dispositivo."
    override val requiredPermissions = setOf(ToolPermission.ANDROID_BRIDGE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val manager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = manager.getSensorList(Sensor.TYPE_ALL)
            .sortedWith(compareBy<Sensor>({ it.type }, { it.name.lowercase() }))

        val output = sensors.joinToString("\n") { sensor ->
            sensor.type.toString() + "\t" +
                sensor.name + "\t" +
                sensor.vendor + "\tversion=" + sensor.version +
                "\tpowerMa=" + sensor.power
        }

        return ToolResponse(
            true,
            output,
            mapOf("count" to sensors.size.toString())
        )
    }
}

fun ToolBroker.registerAndroidTools(context: Context): ToolBroker = apply {
    val app = context.applicationContext
    register(AndroidBatteryTool(app))
    register(AndroidMemoryTool(app))
    register(AndroidStorageTool(app))
    register(AndroidThermalTool(app))
    register(AndroidNetworkTool(app))
    register(AndroidSensorsListTool(app))
    register(ClipboardReadTool(app))
    register(ClipboardWriteTool(app))
    register(AndroidOpenUrlTool(app))
    register(AndroidShareTextTool(app))
    register(AndroidLaunchAppTool(app))

    val toolPacks = ToolPackAssetRepository(app)
    register(ToolPackListTool(toolPacks))
    register(ToolPackInspectTool(toolPacks))
}


object AndroidUiValidators {
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$")

    fun validHttpUrl(value: String): Boolean {
        val lower = value.lowercase()
        return (lower.startsWith("https://") || lower.startsWith("http://")) &&
            value.length <= 4096 &&
            !value.contains('\n') &&
            !value.contains('\r')
    }

    fun validPackageName(value: String): Boolean =
        value.length <= 255 && PACKAGE.matches(value)
}

class AndroidOpenUrlTool(
    private val appContext: Context
) : AgentTool {
    override val id = "android.open_url"
    override val description = "Abre una URL HTTP/HTTPS en una app visible del dispositivo."
    override val requiredPermissions = setOf(
        ToolPermission.ANDROID_BRIDGE,
        ToolPermission.ANDROID_UI_ACTION
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val url = call.arguments["url"]?.takeIf(AndroidUiValidators::validHttpUrl)
            ?: return ToolResponse(false, "URL faltante o inválida")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            appContext.startActivity(intent)
            ToolResponse(true, "URL abierta", mapOf("url" to url))
        }.getOrElse {
            ToolResponse(false, it.message ?: "No se pudo abrir la URL")
        }
    }
}

class AndroidShareTextTool(
    private val appContext: Context
) : AgentTool {
    override val id = "android.share_text"
    override val description = "Abre el selector Android para compartir texto de forma visible."
    override val requiredPermissions = setOf(
        ToolPermission.ANDROID_BRIDGE,
        ToolPermission.ANDROID_UI_ACTION
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val text = call.arguments["text"]?.takeIf { it.isNotBlank() }
            ?: return ToolResponse(false, "Falta text")
        if (text.length > 200_000) return ToolResponse(false, "Texto demasiado grande")

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            call.arguments["title"]?.takeIf { it.isNotBlank() }?.let {
                putExtra(Intent.EXTRA_TITLE, it.take(200))
            }
        }

        val chooser = Intent.createChooser(
            share,
            call.arguments["chooserTitle"]?.takeIf { it.isNotBlank() }?.take(120)
                ?: "Compartir desde AngCode"
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            appContext.startActivity(chooser)
            ToolResponse(true, "Selector de compartir abierto")
        }.getOrElse {
            ToolResponse(false, it.message ?: "No se pudo abrir el selector")
        }
    }
}

class AndroidLaunchAppTool(
    private val appContext: Context
) : AgentTool {
    override val id = "android.launch_app"
    override val description = "Abre la actividad principal de un package Android de forma visible."
    override val requiredPermissions = setOf(
        ToolPermission.ANDROID_BRIDGE,
        ToolPermission.ANDROID_UI_ACTION
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val packageName = call.arguments["package"]
            ?.takeIf(AndroidUiValidators::validPackageName)
            ?: return ToolResponse(false, "package faltante o inválido")

        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ToolResponse(false, "La app no tiene actividad de lanzamiento visible")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            appContext.startActivity(launchIntent)
            ToolResponse(true, "App abierta", mapOf("package" to packageName))
        }.getOrElse {
            ToolResponse(false, it.message ?: "No se pudo abrir la app")
        }
    }
}
