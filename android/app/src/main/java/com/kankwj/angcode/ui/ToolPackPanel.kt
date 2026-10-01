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
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.runtime.ExecutableDiscovery
import com.kankwj.angcode.runtime.InstalledRuntimeInspector
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPackAssetRepository
import com.kankwj.angcode.runtime.ToolPackManifest
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.WorkspaceManager
import com.kankwj.angcode.runtime.registerAndroidTools
import com.kankwj.angcode.runtime.registerCoreTools
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.PanelRaised
import com.kankwj.angcode.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ToolPackPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var installing by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val packs = remember(refresh) {
        ToolPackAssetRepository(context).loadAll()
    }
    val broker = remember(refresh) {
        ToolBroker()
            .registerCoreTools()
            .registerAndroidTools(context)
    }
    val registered = remember(broker) { broker.availableTools().map { it.id }.toSet() }
    val executables = remember(refresh) { ExecutableDiscovery.forApp(context).asMap() }
    val runtime = remember(refresh) { InstalledRuntimeInspector(context).inspect() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Tool Packs",
            color = InkWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            if (runtime.installed) {
                "Runtime instalado · " + executables.size + " ejecutables detectados"
            } else {
                "Instala el Runtime AngCode para activar paquetes Linux."
            },
            color = Muted,
            fontSize = 12.sp
        )

        packs.forEach { pack ->
            ToolPackCard(
                pack = pack,
                registered = registered,
                runtimeInstalled = runtime.installed,
                installing = installing == pack.id,
                canInstall = runtime.installed &&
                    pack.packages.isNotEmpty() &&
                    ("apt-get" in executables || "apt" in executables),
                onInstall = {
                    if (installing != null) return@ToolPackCard
                    installing = pack.id
                    message = null
                    scope.launch {
                        val response = withContext(Dispatchers.IO) {
                            val systemWorkspace = WorkspaceManager(context)
                                .createWorkspace("_system")
                            val installBroker = ToolBroker().registerCoreTools()
                            installBroker.execute(
                                ToolCall(
                                    "package.install",
                                    mapOf(
                                        "packages" to pack.packages.joinToString("\u001F")
                                    )
                                ),
                                ToolContext(
                                    workspace = systemWorkspace,
                                    grantedPermissions = setOf(
                                        ToolPermission.PROCESS_EXECUTE,
                                        ToolPermission.NETWORK,
                                        ToolPermission.PACKAGE_MANAGE
                                    ),
                                    executables = ExecutableDiscovery.forApp(context).asMap()
                                )
                            )
                        }
                        message = if (response.ok) {
                            "✓ " + pack.name + " instalado"
                        } else {
                            "✕ " + response.output.take(500)
                        }
                        installing = null
                        refresh++
                    }
                }
            )
        }

        message?.let {
            Surface(
                color = PanelRaised,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    it,
                    color = InkWhite,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ToolPackCard(
    pack: ToolPackManifest,
    registered: Set<String>,
    runtimeInstalled: Boolean,
    installing: Boolean,
    canInstall: Boolean,
    onInstall: () -> Unit
) {
    val activeTools = pack.exportedTools.count { toolMatchesPack(it, registered) }
    val allToolsActive = activeTools == pack.exportedTools.size && pack.exportedTools.isNotEmpty()

    Surface(
        color = Graphite,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (allToolsActive) Icons.Rounded.CheckCircle else Icons.Rounded.Build,
                    null,
                    tint = if (allToolsActive) Success else AngOrange
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(pack.name, color = InkWhite, fontWeight = FontWeight.Bold)
                    Text(pack.description, color = Muted, fontSize = 11.sp)
                }
                Text(
                    activeTools.toString() + "/" + pack.exportedTools.size,
                    color = if (allToolsActive) Success else AngOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Text(
                "Paquetes: " + if (pack.packages.isEmpty()) "ninguno" else pack.packages.joinToString(", "),
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )

            if (pack.packages.isNotEmpty()) {
                Button(
                    enabled = canInstall && !installing,
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngOrange,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        when {
                            installing -> "Instalando…"
                            !runtimeInstalled -> "Runtime requerido"
                            allToolsActive -> "Revisar / actualizar pack"
                            else -> "Instalar pack"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun toolMatchesPack(
    requested: String,
    registered: Set<String>
): Boolean {
    if (!requested.contains("*")) return requested in registered
    val prefix = requested.substringBefore("*")
    return registered.any { it.startsWith(prefix) }
}
