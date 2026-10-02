package com.kankwj.angcode.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.connectors.LightpandaRuntimeManager
import com.kankwj.angcode.connectors.root.RootBridgeManager
import com.kankwj.angcode.connectors.shizuku.ShizukuBridgeManager
import com.kankwj.angcode.runtime.ExecutableDiscovery
import com.kankwj.angcode.runtime.InstalledRuntimeInspector
import com.kankwj.angcode.runtime.LocalModelStore
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import com.kankwj.angcode.ui.theme.Success
import com.kankwj.angcode.ui.theme.Warning as WarningColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class HealthItem(
    val label: String,
    val ready: Boolean,
    val required: Boolean,
    val detail: String
)

@Composable
fun SystemHealthCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var diagnosticsMessage by remember { mutableStateOf<String?>(null) }
    var pendingBundle by remember { mutableStateOf<java.io.File?>(null) }

    val exportDiagnostics = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val bundle = pendingBundle
        if (uri != null && bundle != null) {
            scope.launch {
                val copied = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            bundle.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        } ?: error("No se pudo abrir el destino")
                    }
                }
                diagnosticsMessage = copied.fold(
                    onSuccess = { "✓ Diagnóstico exportado" },
                    onFailure = { "✕ " + (it.message ?: "Falló la exportación") }
                )
                bundle.delete()
                pendingBundle = null
            }
        } else {
            bundle?.delete()
            pendingBundle = null
        }
    }

    val items = remember(refresh) {
        val runtime = InstalledRuntimeInspector(context).inspect()
        val executables = ExecutableDiscovery.forApp(context).asMap()
        val model = LocalModelStore(context).active()
        val browser = LightpandaRuntimeManager(context).status()
        val shizuku = ShizukuBridgeManager.status()
        val root = RootBridgeManager.status(context)

        listOf(
            HealthItem(
                "Runtime AngCode",
                runtime.installed,
                true,
                if (runtime.installed) {
                    runtime.executableCount.toString() + " ejecutables"
                } else {
                    "No instalado"
                }
            ),
            HealthItem(
                "Modelo GGUF",
                model != null,
                true,
                model?.name ?: "Sin modelo activo"
            ),
            HealthItem(
                "llama.cpp",
                "llama-server" in executables || "llama-cli" in executables,
                true,
                when {
                    "llama-server" in executables -> "llama-server"
                    "llama-cli" in executables -> "llama-cli"
                    else -> "No disponible"
                }
            ),
            HealthItem(
                "PRoot-Distro",
                "proot-distro" in executables,
                true,
                executables["proot-distro"] ?: "No disponible"
            ),
            HealthItem(
                "Git",
                "git" in executables,
                true,
                executables["git"] ?: "No disponible"
            ),
            HealthItem(
                "Python",
                "python" in executables,
                false,
                executables["python"] ?: "Tool Pack no instalado"
            ),
            HealthItem(
                "Node",
                "node" in executables,
                false,
                executables["node"] ?: "Tool Pack no instalado"
            ),
            HealthItem(
                "Clang",
                "clang" in executables,
                false,
                executables["clang"] ?: "Tool Pack no instalado"
            ),
            HealthItem(
                "Android toolchain",
                listOf("java", "gradle", "aapt2", "d8", "apksigner")
                    .all { it in executables },
                false,
                listOf("java", "gradle", "aapt2", "d8", "apksigner")
                    .filterNot { it in executables }
                    .let { missing ->
                        if (missing.isEmpty()) "Completo"
                        else "Faltan: " + missing.joinToString()
                    }
            ),
            HealthItem(
                "Lightpanda",
                browser.installed,
                false,
                when {
                    browser.running ->
                        "MCP activo · " + browser.registeredTools + " tools"
                    browser.installed -> "Instalado"
                    else -> "Opcional"
                }
            ),
            HealthItem(
                "Shizuku",
                shizuku.serviceBound,
                false,
                when {
                    shizuku.serviceBound ->
                        "Conectado · " + shizuku.privilegeLabel
                    shizuku.permissionGranted -> "Autorizado, servicio no enlazado"
                    shizuku.binderAlive -> "Disponible, sin permiso"
                    else -> "Opcional"
                }
            ),
            HealthItem(
                "Root",
                root.root,
                false,
                root.detail
            )
        )
    }

    val requiredMissing = items.count { it.required && !it.ready }

    Surface(
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row {
                Icon(
                    Icons.Rounded.HealthAndSafety,
                    null,
                    tint = if (requiredMissing == 0) Success else AngOrange
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Autodiagnóstico",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (requiredMissing == 0) {
                            "Base local preparada"
                        } else {
                            requiredMissing.toString() +
                                " requisito(s) base pendiente(s)"
                        },
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
                OutlinedButton(onClick = { refresh++ }) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Revisar", fontSize = 9.sp)
                }
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val bundle = runCatching {
                            withContext(Dispatchers.IO) {
                                DiagnosticBundleManager(context).create()
                            }
                        }.getOrElse { error ->
                            diagnosticsMessage = "✕ " +
                                (error.message ?: "No se pudo crear diagnóstico")
                            return@launch
                        }

                        pendingBundle = bundle
                        exportDiagnostics.launch(bundle.name)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(6.dp))
                Text("Exportar diagnóstico", fontSize = 10.sp)
            }

            diagnosticsMessage?.let {
                Text(
                    it,
                    color = Muted,
                    fontSize = 9.sp
                )
            }

            items.forEach { item ->
                Surface(
                    color = Graphite,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 11.dp,
                            vertical = 9.dp
                        )
                    ) {
                        Icon(
                            if (item.ready) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.Warning
                            },
                            null,
                            tint = if (item.ready) {
                                Success
                            } else if (item.required) {
                                WarningColor
                            } else {
                                Muted
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.label +
                                    if (item.required) " · BASE" else " · OPCIONAL",
                                color = InkWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                            Text(
                                item.detail,
                                color = Muted,
                                fontSize = 9.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}
