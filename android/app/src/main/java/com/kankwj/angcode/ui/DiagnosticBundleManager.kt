package com.kankwj.angcode.ui

import android.content.Context
import android.os.Build
import com.kankwj.angcode.BuildConfig
import com.kankwj.angcode.connectors.LightpandaRuntimeManager
import com.kankwj.angcode.connectors.root.RootBridgeManager
import com.kankwj.angcode.connectors.shizuku.ShizukuBridgeManager
import com.kankwj.angcode.runtime.ExecutableDiscovery
import com.kankwj.angcode.runtime.InstalledRuntimeInspector
import com.kankwj.angcode.runtime.LocalModelStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiagnosticBundleManager(
    private val context: Context
) {
    private val appContext = context.applicationContext

    fun create(): File {
        val out = File(
            appContext.cacheDir,
            "angcode-diagnostics-" + System.currentTimeMillis() + ".zip"
        )

        val runtime = InstalledRuntimeInspector(appContext).inspect()
        val executables = ExecutableDiscovery.forApp(appContext).asMap()
        val model = LocalModelStore(appContext).active()
        val shizuku = ShizukuBridgeManager.status()
        val root = RootBridgeManager.status(appContext)
        val browser = LightpandaRuntimeManager(appContext).status()

        val rootJson = JSONObject()
            .put("formatVersion", 1)
            .put("createdAtMillis", System.currentTimeMillis())
            .put(
                "app",
                JSONObject()
                    .put("package", appContext.packageName)
                    .put("versionName", BuildConfig.VERSION_NAME)
                    .put("versionCode", BuildConfig.VERSION_CODE)
                    .put("debug", BuildConfig.DEBUG)
            )
            .put(
                "device",
                JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("device", Build.DEVICE)
                    .put("android", Build.VERSION.RELEASE)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            )
            .put(
                "runtime",
                JSONObject()
                    .put("installed", runtime.installed)
                    .put("prefix", runtime.prefix)
                    .put("markerSha256", runtime.markerSha256 ?: JSONObject.NULL)
                    .put("executableCount", runtime.executableCount)
                    .put(
                        "executableIds",
                        JSONArray(executables.keys.sorted())
                    )
            )
            .put(
                "model",
                if (model == null) {
                    JSONObject.NULL
                } else {
                    JSONObject()
                        .put("name", model.name)
                        .put("bytes", model.bytes)
                        .put("sha256", model.sha256 ?: JSONObject.NULL)
                        .put("active", model.active)
                }
            )
            .put(
                "browser",
                JSONObject()
                    .put("installed", browser.installed)
                    .put("running", browser.running)
                    .put("registeredTools", browser.registeredTools)
                    .put("sandboxName", browser.sandboxName ?: JSONObject.NULL)
            )
            .put(
                "shizuku",
                JSONObject()
                    .put("binderAlive", shizuku.binderAlive)
                    .put("permissionGranted", shizuku.permissionGranted)
                    .put("apiVersion", shizuku.apiVersion ?: JSONObject.NULL)
                    .put("uid", shizuku.uid ?: JSONObject.NULL)
                    .put("serviceBound", shizuku.serviceBound)
                    .put("privilege", shizuku.privilegeLabel)
            )
            .put(
                "root",
                JSONObject()
                    .put("enabledByUser", root.enabledByUser)
                    .put("shellCached", root.shellCached)
                    .put("root", root.root)
                    .put("detail", root.detail)
            )

        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("diagnostics.json"))
            zip.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(
                buildString {
                    appendLine("AngCode diagnostics bundle")
                    appendLine()
                    appendLine("No API tokens, GitHub tokens, SSH keys, model contents,")
                    appendLine("clipboard contents, browser cookies or project source files")
                    appendLine("are included by this exporter.")
                    appendLine()
                    appendLine("Main payload: diagnostics.json")
                }.toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()
        }

        return out
    }
}
