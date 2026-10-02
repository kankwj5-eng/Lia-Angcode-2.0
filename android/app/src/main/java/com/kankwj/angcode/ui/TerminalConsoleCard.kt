package com.kankwj.angcode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.runtime.ActiveProjectStore
import com.kankwj.angcode.runtime.ExecutableDiscovery
import com.kankwj.angcode.runtime.ExecutionPolicy
import com.kankwj.angcode.runtime.ProcessRegistry
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.registerCoreTools
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TerminalConsoleCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val registry = remember { ProcessRegistry() }
    val policy = remember {
        ExecutionPolicy.androidBase(
            runtimeRoots = listOf(File(context.filesDir, "usr"))
        )
    }
    val broker = remember {
        ToolBroker().registerCoreTools(
            policy = policy,
            processRegistry = registry
        )
    }

    val workspace = remember {
        ActiveProjectStore(context).resolveActiveOrScratch()
    }
    val executables = remember {
        ExecutableDiscovery.forApp(context).asMap()
    }
    val shell = executables["bash"] ?: "/system/bin/sh"

    val toolContext = remember {
        ToolContext(
            workspace = workspace,
            grantedPermissions = setOf(
                ToolPermission.PROCESS_EXECUTE,
                ToolPermission.UNRESTRICTED_SHELL,
                ToolPermission.WORKSPACE_READ,
                ToolPermission.WORKSPACE_WRITE
            ),
            executables = executables
        )
    }

    var processId by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf("Consola detenida.") }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(processId) {
        val id = processId ?: return@LaunchedEffect
        while (processId == id) {
            val response = withContext(Dispatchers.IO) {
                broker.execute(
                    ToolCall(
                        "process.logs",
                        mapOf("processId" to id)
                    ),
                    toolContext
                )
            }

            if (response.ok) {
                logs = response.output
                running = response.metadata["running"] == "true"
                if (!running) break
            } else {
                error = response.output
                break
            }
            delay(500)
        }
    }

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
                Icon(Icons.Rounded.Terminal, null, tint = AngOrange)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Consola persistente",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        (if (shell.endsWith("bash")) "bash" else "Android sh") +
                            " · " + workspace.name,
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            Surface(
                color = Graphite,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 300.dp)
            ) {
                Text(
                    logs.ifBlank { "(sin salida)" },
                    color = Color(0xFFE2E3E7),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            if (processId == null || !running) {
                Button(
                    onClick = {
                        error = null
                        scope.launch {
                            val response = withContext(Dispatchers.IO) {
                                broker.execute(
                                    ToolCall(
                                        "process.start",
                                        mapOf(
                                            "executable" to shell,
                                            "interactive" to "true"
                                        )
                                    ),
                                    toolContext
                                )
                            }

                            if (response.ok) {
                                processId = response.metadata["processId"]
                                    ?: response.output
                                running = true
                                logs = "Sesión iniciada."
                            } else {
                                error = response.output
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngOrange,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir consola", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Comando") }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = input.isNotBlank(),
                        onClick = {
                            val id = processId ?: return@Button
                            val command = input
                            input = ""
                            scope.launch {
                                val response = withContext(Dispatchers.IO) {
                                    broker.execute(
                                        ToolCall(
                                            "process.write",
                                            mapOf(
                                                "processId" to id,
                                                "input" to command,
                                                "newline" to "true"
                                            )
                                        ),
                                        toolContext
                                    )
                                }
                                if (!response.ok) error = response.output
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngOrange,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Send, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Enviar", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val id = processId ?: return@OutlinedButton
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    broker.execute(
                                        ToolCall(
                                            "process.stop",
                                            mapOf("processId" to id)
                                        ),
                                        toolContext
                                    )
                                }
                                running = false
                                processId = null
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

            error?.let {
                Text(
                    "✕ " + it,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    fontSize = 10.sp
                )
            }

            Text(
                "Esta consola tiene permiso de shell solo porque la abriste manualmente. Los agentes no reciben UNRESTRICTED_SHELL por defecto.",
                color = Muted,
                fontSize = 9.sp,
                lineHeight = 12.sp
            )
        }
    }
}
