package com.kankwj.angcode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.WorkspaceManager
import com.kankwj.angcode.runtime.registerCoreTools
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolExecutionCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val broker = remember { ToolBroker().registerCoreTools() }
    val workspace = remember { WorkspaceManager(context).createWorkspace("_diagnostics") }
    var output by remember { mutableStateOf("Todavía no se ha ejecutado el diagnóstico.") }
    var running by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tool Broker en vivo", color = InkWhite, fontWeight = FontWeight.Bold)
            Text(
                "Ejecuta toybox directamente, sin shell arbitraria, y devuelve la salida estructurada.",
                color = Muted
            )
            Text(
                output,
                color = Color(0xFFD7D9DE),
                fontFamily = FontFamily.Monospace
            )
            Button(
                onClick = {
                    if (running) return@Button
                    running = true
                    scope.launch {
                        val response = withContext(Dispatchers.IO) {
                            broker.execute(
                                ToolCall(
                                    toolId = "process.exec",
                                    arguments = mapOf(
                                        "executable" to "/system/bin/toybox",
                                        "args" to listOf("uname", "-a").joinToString("\u001F")
                                    )
                                ),
                                ToolContext(
                                    workspace = workspace,
                                    grantedPermissions = setOf(ToolPermission.PROCESS_EXECUTE)
                                )
                            )
                        }
                        output = if (response.ok) response.output.ifBlank { "(sin salida)" } else "DENEGADO/ERROR: ${response.output}"
                        running = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AngOrange, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (running) "Ejecutando…" else "Ejecutar diagnóstico")
            }
        }
    }
}
