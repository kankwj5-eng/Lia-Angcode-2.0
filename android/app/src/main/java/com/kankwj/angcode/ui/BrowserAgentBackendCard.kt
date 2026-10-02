package com.kankwj.angcode.ui

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
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.connectors.ConnectorSessionRegistry
import com.kankwj.angcode.connectors.LightpandaBinaryStore
import com.kankwj.angcode.connectors.LightpandaRuntimeManager
import com.kankwj.angcode.runtime.ActiveProjectStore
import com.kankwj.angcode.runtime.ExecutableDiscovery
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.registerCoreTools
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.PanelRaised
import com.kankwj.angcode.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun BrowserAgentBackendCard(
    onTakeControl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val binaryStore = remember { LightpandaBinaryStore(context) }
    val manager = remember { LightpandaRuntimeManager(context) }
    val projectStore = remember { ActiveProjectStore(context) }

    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val binaryInstalled = remember(refresh) { binaryStore.installedBinary() != null }
    val executables = remember(refresh) { ExecutableDiscovery.forApp(context).asMap() }
    val prootReady = "proot-distro" in executables
    val connected = ConnectorSessionRegistry.get("browser") != null && manager.running()

    Surface(
        color = PanelRaised,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row {
                Icon(
                    if (connected) Icons.Rounded.CheckCircle else Icons.Rounded.Public,
                    null,
                    tint = if (connected) Success else AngOrange
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Navegador agente",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        when {
                            connected -> "Lightpanda MCP conectado"
                            !prootReady -> "Runtime con PRoot requerido"
                            !binaryInstalled -> "Lightpanda aún no instalado"
                            else -> "Listo para preparar contenedor"
                        },
                        color = Muted,
                        fontSize = 9.sp
                    )
                }
            }

            if (!binaryInstalled) {
                Button(
                    enabled = !busy && prootReady,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                binaryStore.install()
                            }
                            message = if (result.success) {
                                "✓ " + result.detail
                            } else {
                                "✕ " + result.detail
                            }
                            busy = false
                            refresh++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngOrange,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CloudDownload, null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (busy) "Descargando…" else "Instalar Lightpanda (~190 MB)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            } else if (!connected) {
                Button(
                    enabled = !busy && prootReady,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val workspace = projectStore.resolveActiveOrScratch()
                                val broker = ToolBroker().registerCoreTools()
                                val toolContext = ToolContext(
                                    workspace = workspace,
                                    grantedPermissions = setOf(
                                        ToolPermission.WORKSPACE_READ,
                                        ToolPermission.PROCESS_EXECUTE,
                                        ToolPermission.NETWORK,
                                        ToolPermission.SANDBOX_MANAGE
                                    ),
                                    executables = ExecutableDiscovery.forApp(context).asMap()
                                )

                                val listing = broker.execute(
                                    ToolCall("sandbox.list"),
                                    toolContext
                                )
                                if (!listing.ok) {
                                    return@withContext "✕ " + listing.output
                                }

                                if (!listing.output.contains("angcode-browser")) {
                                    val install = broker.execute(
                                        ToolCall(
                                            "sandbox.install",
                                            mapOf(
                                                "image" to "debian",
                                                "name" to "angcode-browser"
                                            )
                                        ),
                                        toolContext
                                    )
                                    if (!install.ok) {
                                        return@withContext "✕ " + install.output
                                    }
                                }

                                val started = manager.start(
                                    context = toolContext,
                                    sandboxName = "angcode-browser",
                                    port = 9223
                                )
                                if (started.success) {
                                    "✓ " + started.detail
                                } else {
                                    "✕ " + started.detail
                                }
                            }

                            message = result
                            busy = false
                            refresh++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngOrange,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Public, null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (busy) "Preparando…" else "Preparar y conectar navegador agente",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                val handoff = withContext(Dispatchers.IO) {
                                    val client = ConnectorSessionRegistry.get("browser")
                                        ?: return@withContext BrowserHandoff(
                                            null,
                                            "Sesión MCP no disponible"
                                        )
                                    val result = client.callTool(
                                        "session_list",
                                        emptyMap()
                                    )
                                    if (result.isError) {
                                        return@withContext BrowserHandoff(
                                            null,
                                            result.text.ifBlank {
                                                "session_list falló"
                                            }
                                        )
                                    }
                                    parseCurrentBrowserUrl(result.text)
                                }

                                if (handoff.url != null) {
                                    onTakeControl(handoff.url)
                                    message =
                                        "✓ URL abierta en navegador visible"
                                } else {
                                    message = "✕ " + handoff.detail
                                }
                                busy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngOrange,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Abrir página", fontSize = 9.sp)
                    }

                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    manager.stop()
                                }
                                message = "Navegador agente detenido"
                                busy = false
                                refresh++
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Stop, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Detener", fontSize = 9.sp)
                    }
                }
            }

            message?.let {
                Text(
                    it.take(1200),
                    color = if (it.startsWith("✓")) Success else Muted,
                    fontSize = 9.sp,
                    lineHeight = 12.sp
                )
            }

            Text(
                "El navegador visible de abajo es para ti. Lightpanda es el navegador separado que usa la IA.",
                color = Muted,
                fontSize = 9.sp
            )
        }
    }
}


private data class BrowserHandoff(
    val url: String?,
    val detail: String
)

private fun parseCurrentBrowserUrl(raw: String): BrowserHandoff {
    return runCatching {
        val array = JSONArray(raw)
        var fallback: String? = null

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val url = item.optString("url")
                .takeIf {
                    it.startsWith("http://") ||
                        it.startsWith("https://")
                }
                ?: continue

            if (item.optString("id") == "default") {
                return BrowserHandoff(url, "default")
            }
            if (fallback == null) fallback = url
        }

        if (fallback != null) {
            BrowserHandoff(fallback, "primera sesión activa")
        } else {
            BrowserHandoff(
                null,
                "El agente no tiene una página HTTP/HTTPS abierta"
            )
        }
    }.getOrElse { error ->
        BrowserHandoff(
            null,
            "Respuesta session_list inválida: " +
                (error.message ?: "JSON inválido")
        )
    }
}
