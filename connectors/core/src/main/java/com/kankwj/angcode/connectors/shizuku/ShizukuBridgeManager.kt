package com.kankwj.angcode.connectors.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.kankwj.angcode.runtime.AgentTool
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.ToolResponse
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class ShizukuBridgeStatus(
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val uid: Int?,
    val apiVersion: Int?,
    val serviceBound: Boolean
) {
    val privilegeLabel: String
        get() = when (uid) {
            0 -> "root"
            2000 -> "adb-shell"
            null -> "none"
            else -> "uid-" + uid
        }
}

object ShizukuBridgeManager {
    private const val SERVICE_VERSION = 1
    private const val SERVICE_TAG = "angcode-privileged-v1"

    @Volatile
    private var service: IAngCodePrivilegedService? = null

    fun status(): ShizukuBridgeStatus {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            return ShizukuBridgeStatus(false, false, null, null, false)
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        val uid = if (granted) runCatching { Shizuku.getUid() }.getOrNull() else null
        val version = runCatching { Shizuku.getVersion() }.getOrNull()
        val bound = service?.asBinder()?.pingBinder() == true

        return ShizukuBridgeStatus(
            binderAlive = true,
            permissionGranted = granted,
            uid = uid,
            apiVersion = version,
            serviceBound = bound
        )
    }

    fun observe(
        onPermissionResult: (granted: Boolean) -> Unit,
        onBinderReceived: () -> Unit,
        onBinderDead: () -> Unit
    ): AutoCloseable {
        val permissionListener =
            Shizuku.OnRequestPermissionResultListener { _, grantResult ->
                onPermissionResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        val receivedListener = Shizuku.OnBinderReceivedListener {
            onBinderReceived()
        }
        val deadListener = Shizuku.OnBinderDeadListener {
            service = null
            onBinderDead()
        }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(receivedListener)
        Shizuku.addBinderDeadListener(deadListener)

        return AutoCloseable {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(receivedListener)
            Shizuku.removeBinderDeadListener(deadListener)
        }
    }

    fun requestPermission(requestCode: Int = 8401): Boolean {
        if (!Shizuku.pingBinder()) return false
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
        Shizuku.requestPermission(requestCode)
        return false
    }

    fun bind(
        context: Context,
        timeoutMillis: Long = 10_000
    ): Boolean {
        val current = service
        if (current?.asBinder()?.pingBinder() == true) return true

        val status = status()
        if (!status.binderAlive || !status.permissionGranted) return false
        if ((status.apiVersion ?: 0) < 10) return false

        val latch = CountDownLatch(1)
        var connected: IAngCodePrivilegedService? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                connected = IAngCodePrivilegedService.Stub.asInterface(binder)
                service = connected
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
                latch.countDown()
            }
        }

        val args = Shizuku.UserServiceArgs(
            ComponentName(
                context.packageName,
                AngCodePrivilegedService::class.java.name
            )
        )
            .daemon(false)
            .processNameSuffix("angcode")
            .debuggable(false)
            .version(SERVICE_VERSION)
            .tag(SERVICE_TAG)

        return runCatching {
            Shizuku.bindUserService(args, connection)
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS) &&
                connected?.asBinder()?.pingBinder() == true
        }.getOrDefault(false)
    }

    fun run(command: List<String>, timeoutMillis: Int = 30_000): ToolResponse {
        val remote = service
            ?: return ToolResponse(false, "Shizuku UserService no conectado")

        val output = runCatching {
            remote.run(command.toTypedArray(), timeoutMillis)
        }.getOrElse {
            service = null
            return ToolResponse(false, it.message ?: "Falló Shizuku")
        }

        val ok = output.startsWith("exit=0")
        return ToolResponse(
            ok = ok,
            output = output,
            metadata = mapOf(
                "backend" to "shizuku",
                "uid" to (status().uid?.toString() ?: "")
            )
        )
    }

    fun captureScreen(output: File): ToolResponse {
        val remote = service
            ?: return ToolResponse(false, "Shizuku UserService no conectado")

        output.parentFile?.mkdirs()
        val descriptor = ParcelFileDescriptor.open(
            output,
            ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_TRUNCATE
        )

        val response = runCatching {
            remote.captureScreen(descriptor)
        }.getOrElse {
            runCatching { descriptor.close() }
            service = null
            output.delete()
            return ToolResponse(false, it.message ?: "Falló captura Shizuku")
        }

        val validPng = isPngFile(output)
        val ok = response.startsWith("exit=0") && validPng

        if (!ok) {
            output.delete()
        }

        return ToolResponse(
            ok = ok,
            output = if (ok) {
                output.absolutePath
            } else {
                response + if (!validPng) "\nPNG inválido" else ""
            },
            metadata = mapOf(
                "backend" to "shizuku",
                "bytes" to if (output.exists()) output.length().toString() else "0"
            )
        )
    }

    fun installApk(
        apk: File,
        replaceExisting: Boolean = true
    ): ToolResponse {
        val remote = service
            ?: return ToolResponse(false, "Shizuku UserService no conectado")
        if (!apk.isFile) return ToolResponse(false, "APK no encontrado")

        val descriptor = ParcelFileDescriptor.open(
            apk,
            ParcelFileDescriptor.MODE_READ_ONLY
        )

        val output = runCatching {
            remote.installApk(descriptor, apk.length(), replaceExisting)
        }.getOrElse {
            runCatching { descriptor.close() }
            service = null
            return ToolResponse(false, it.message ?: "Falló instalación Shizuku")
        }

        val ok = output.startsWith("exit=0") && output.contains("Success", ignoreCase = true)
        return ToolResponse(
            ok = ok,
            output = output,
            metadata = mapOf(
                "backend" to "shizuku",
                "apkBytes" to apk.length().toString()
            )
        )
    }
}

class ShizukuStatusTool : AgentTool {
    override val id = "android.shizuku.status"
    override val description = "Consulta si Shizuku está vivo, autorizado y con qué UID."
    override val requiredPermissions = emptySet<ToolPermission>()

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val status = ShizukuBridgeManager.status()
        return ToolResponse(
            true,
            buildString {
                appendLine("binderAlive=" + status.binderAlive)
                appendLine("permissionGranted=" + status.permissionGranted)
                appendLine("apiVersion=" + (status.apiVersion ?: -1))
                appendLine("uid=" + (status.uid ?: -1))
                append("privilege=" + status.privilegeLabel)
            },
            mapOf("serviceBound" to status.serviceBound.toString())
        )
    }
}

class ShizukuPackageInfoTool : AgentTool {
    override val id = "android.shizuku.package_info"
    override val description = "Obtiene dumpsys package para un package Android válido."
    override val requiredPermissions = setOf(ToolPermission.SHIZUKU_PRIVILEGED)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val packageName = call.arguments["package"]
            ?.takeIf(::validPackageName)
            ?: return ToolResponse(false, "package faltante o inválido")
        return ShizukuBridgeManager.run(
            listOf("dumpsys", "package", packageName),
            30_000
        )
    }
}

class ShizukuLaunchAppTool : AgentTool {
    override val id = "android.shizuku.launch"
    override val description = "Lanza la actividad launcher de un package mediante shell Android."
    override val requiredPermissions = setOf(ToolPermission.SHIZUKU_PRIVILEGED)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val packageName = call.arguments["package"]
            ?.takeIf(::validPackageName)
            ?: return ToolResponse(false, "package faltante o inválido")

        return ShizukuBridgeManager.run(
            listOf(
                "monkey",
                "-p", packageName,
                "-c", "android.intent.category.LAUNCHER",
                "1"
            ),
            30_000
        )
    }
}

class ShizukuLogcatTool : AgentTool {
    override val id = "android.shizuku.logcat"
    override val description = "Lee las últimas líneas de logcat con privilegios Shizuku."
    override val requiredPermissions = setOf(ToolPermission.SHIZUKU_PRIVILEGED)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val lines = call.arguments["lines"]
            ?.toIntOrNull()
            ?.coerceIn(20, 2000)
            ?: 300
        return ShizukuBridgeManager.run(
            listOf("logcat", "-d", "-t", lines.toString()),
            45_000
        )
    }
}

class ShizukuInstallApkTool : AgentTool {
    override val id = "android.shizuku.apk_install"
    override val description = "Instala un APK del workspace usando stdin de pm install."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.SHIZUKU_PRIVILEGED
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"]
            ?: return ToolResponse(false, "Falta path")
        val root = context.workspace.canonicalFile
        val apk = File(root, path).canonicalFile
        if (!apk.toPath().startsWith(root.toPath())) {
            return ToolResponse(false, "Ruta fuera del workspace")
        }
        if (!apk.isFile || !apk.extension.equals("apk", ignoreCase = true)) {
            return ToolResponse(false, "APK no encontrado")
        }
        return ShizukuBridgeManager.installApk(
            apk = apk,
            replaceExisting = call.arguments["replace"]?.toBooleanStrictOrNull() ?: true
        )
    }
}

class ShizukuScreenshotTool : AgentTool {
    override val id = "android.shizuku.screenshot"
    override val description = "Captura la pantalla mediante screencap privilegiado y guarda PNG en artifacts/."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_WRITE,
        ToolPermission.SHIZUKU_PRIVILEGED
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val artifacts = File(context.workspace, "artifacts").apply { mkdirs() }
        val requested = call.arguments["name"]
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.take(100)
            ?.takeIf { it.isNotBlank() }
        val fileName = when {
            requested == null ->
                "screenshot-" + System.currentTimeMillis() + ".png"
            requested.endsWith(".png", ignoreCase = true) ->
                requested
            else ->
                requested + ".png"
        }

        val output = File(artifacts, fileName).canonicalFile
        if (!output.toPath().startsWith(artifacts.canonicalFile.toPath())) {
            return ToolResponse(false, "Ruta de captura inválida")
        }

        val result = ShizukuBridgeManager.captureScreen(output)
        return if (result.ok) {
            result.copy(
                output = output.relativeTo(context.workspace).invariantSeparatorsPath,
                metadata = result.metadata + mapOf(
                    "artifact" to output.relativeTo(context.workspace).invariantSeparatorsPath
                )
            )
        } else {
            result
        }
    }
}

fun ToolBroker.registerShizukuTools(): ToolBroker = apply {
    register(ShizukuStatusTool())
    register(ShizukuPackageInfoTool())
    register(ShizukuLaunchAppTool())
    register(ShizukuLogcatTool())
    register(ShizukuInstallApkTool())
    register(ShizukuScreenshotTool())
}

internal fun isPngFile(file: File): Boolean {
    if (!file.isFile || file.length() < 8L) return false
    val expected = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47,
        0x0D, 0x0A, 0x1A, 0x0A
    )
    val actual = ByteArray(8)
    file.inputStream().use { input ->
        if (input.read(actual) != actual.size) return false
    }
    return actual.contentEquals(expected)
}

private fun validPackageName(value: String): Boolean =
    value.length in 3..220 &&
        value.matches(Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$"))
