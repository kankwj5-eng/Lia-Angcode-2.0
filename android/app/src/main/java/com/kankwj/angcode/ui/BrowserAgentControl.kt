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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.kankwj.angcode.connectors.LightpandaRuntimeManager
import com.kankwj.angcode.connectors.registerLightpandaTools
import com.kankwj.angcode.runtime.ExecutableDiscovery
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.WorkspaceManager
import com.kankwj.angcode.runtime.registerCoreTools
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BrowserAgentControl() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { LightpandaRuntimeManager(context) }
    val broker = remember {
        ToolBroker()
            .registerCoreTools()
            .registerLightpandaTools(context)
    }
    val workspace = remember {
        WorkspaceManager(context).createWorkspace("_browser")
    }

    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val status = remember(refresh) { manager.status() }
    val executables = remember(refresh) { ExecutableDiscovery.forApp(context).asMap() }
    val prootReady = "proot-distro" in executables

    val toolContext = remember(refresh) {
        ToolContext(
            workspace = workspace,
            grantedPermissions = setOf(
                ToolPermission.NETWORK,
                ToolPermission.PRIVATE_NETWORK,
                ToolPermission.MCP_EXTERNAL,
                ToolPermission.PROCESS_EXECUTE,
                ToolPermission.SANDBOX_MANAGE
            ),
            executables = executables
        )
    }

    Surface(
        color = Graphite,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row {
                Icon(
                    if (status.running) Icons.Rounded.CheckCircle else Icons.Rounded.Public,
                    null,
                    tint = if (status.running) Success else AngOrange
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Navegador del agente",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        when {
                            status.running ->
                                "MCP activo · " + status.registeredTools + " herramientas"
                            !status.installed ->
                                "Lightpanda no instalado"
                            !prootReady ->
                                "Lightpanda listo · falta runtime PRoot"
                            else ->
                                "Listo para iniciar"
                        },
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!status.installed) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                val response = withContext(Dispatchers.IO) {
                                    broker.execute(
                                        ToolCall("browser_runtime.install"),
                                        toolContext
                                    )
                                }
                                message = response.output
                                busy = false
                                refresh++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngOrange,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (busy) "Instalando…" else "Instalar", fontSize = 10.sp)
                    }
                } else if (!status.running) {
                    Button(
                        enabled = !busy && prootReady,
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                val response = withContext(Dispatchers.IO) {
                                    broker.execute(
                                        ToolCall("browser_runtime.start"),
                                        toolContext
                                    )
                                }
                                message = response.output
                                busy = false
                                refresh++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngOrange,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (busy) "Iniciando…" else "Iniciar agente", fontSize = 10.sp)
                    }
                } else {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val response = withContext(Dispatchers.IO) {
                                    broker.execute(
                                        ToolCall("browser_runtime.stop"),
                                        toolContext
                                    )
                                }
                                message = response.output
                                busy = false
                                refresh++
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Stop, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Detener", fontSize = 10.sp)
                    }
                }
            }

            message?.let {
                Text(
                    it.take(700),
                    color = Muted,
                    fontSize = 9.sp,
                    lineHeight = 12.sp
                )
            }
        }
    }
}
