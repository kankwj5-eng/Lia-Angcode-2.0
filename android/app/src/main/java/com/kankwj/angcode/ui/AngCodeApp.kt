package com.kankwj.angcode.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.agents.AgentCell
import com.kankwj.angcode.agents.AgentRole
import com.kankwj.angcode.agents.AgentStatus
import com.kankwj.angcode.agents.DirectorEngine
import com.kankwj.angcode.connectors.ConnectorRegistry
import com.kankwj.angcode.runtime.ImportResult
import com.kankwj.angcode.runtime.RuntimeHealth
import com.kankwj.angcode.runtime.RuntimeProbe
import com.kankwj.angcode.runtime.SystemCommandTool
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.WorkspaceManager
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Obsidian
import com.kankwj.angcode.ui.theme.Panel
import com.kankwj.angcode.ui.theme.PanelRaised
import com.kankwj.angcode.ui.theme.Success
import com.kankwj.angcode.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainSection(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Rounded.Home),
    PROJECTS("Proyectos", Icons.Rounded.Folder),
    TOOLS("Herramientas", Icons.Rounded.Build),
    AGENTS("Agentes", Icons.Rounded.Memory),
    SETTINGS("Ajustes", Icons.Rounded.Settings)
}

@Composable
fun AngCodeApp() {
    var section by remember { mutableStateOf(MainSection.HOME) }

    Scaffold(
        containerColor = Obsidian,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = Graphite,
                tonalElevation = 0.dp
            ) {
                MainSection.entries.forEach { item ->
                    NavigationBarItem(
                        selected = section == item,
                        onClick = { section = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AngOrange,
                            selectedTextColor = InkWhite,
                            indicatorColor = PanelRaised,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Obsidian)
        ) {
            when (section) {
                MainSection.HOME -> DashboardScreen()
                MainSection.PROJECTS -> ProjectsScreen()
                MainSection.TOOLS -> ToolsScreen()
                MainSection.AGENTS -> AgentsScreen()
                MainSection.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun DashboardScreen() {
    val director = remember { DirectorEngine() }
    val plan = remember { director.bootstrapPlan() }
    val agents = remember { director.cellsFor(plan) }
    val broker = remember { ToolBroker().registerCoreTools() }
    var health by remember { mutableStateOf<RuntimeHealth?>(null) }

    LaunchedEffect(Unit) {
        health = withContext(Dispatchers.IO) { RuntimeProbe().inspect() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Header() }
        item { HeroCard(health = health, toolCount = broker.availableTools().size) }
        item { SectionTitle("Misión activa", "${plan.tasks.count { it.status == AgentStatus.DONE }}/${plan.tasks.size}") }
        item { MissionCard() }
        item { SectionTitle("Agentes", "${agents.count { it.status == AgentStatus.WORKING }} activos") }
        items(agents.take(4)) { agent -> AgentRow(agent) }
        item { RuntimeCard(health) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(AngOrange, CircleShape))
                Spacer(Modifier.width(9.dp))
                Text("ANGCODE", color = InkWhite, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Text("Agentic mobile workstation", color = Muted, fontSize = 12.sp)
        }
        Surface(
            color = Panel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.border(1.dp, Color(0xFF2B2D34), RoundedCornerShape(14.dp))
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "Ajustes", tint = InkWhite, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun HeroCard(health: RuntimeHealth?, toolCount: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = AngOrange, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Bolt, null, tint = Color.Black, modifier = Modifier.padding(10.dp).size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Estación lista para crecer", color = InkWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("IA + agentes + herramientas + sandbox", color = Muted, fontSize = 13.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("Runtime", health?.ready == true)
                StatusPill("${toolCount} tools", toolCount > 0)
                StatusPill("Local", true)
            }

            Text(
                "Dale una misión. El Director la divide, asigna celdas y usa herramientas sin convertir la app en una terminal gigante.",
                color = Color(0xFFD6D7DB),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(containerColor = AngOrange, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Nueva misión", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MissionCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Graphite),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF272930), RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Code, null, tint = AngOrange)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Construir runtime inicial", color = InkWhite, fontWeight = FontWeight.SemiBold)
                    Text("Arquitectura + UI + Tool Broker", color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.ArrowForward, null, tint = Muted)
            }
            LinearProgressIndicator(
                progress = { .34f },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = AngOrange,
                trackColor = PanelRaised
            )
        }
    }
}

@Composable
private fun AgentRow(agent: AgentCell) {
    val icon = when (agent.role) {
        AgentRole.DIRECTOR -> Icons.Rounded.Memory
        AgentRole.CODER -> Icons.Rounded.Code
        AgentRole.RESEARCHER -> Icons.Rounded.Public
        AgentRole.TESTER -> Icons.Rounded.CheckCircle
        AgentRole.BUILDER -> Icons.Rounded.Build
        AgentRole.BROWSER -> Icons.Rounded.Computer
    }
    val stateColor = when (agent.status) {
        AgentStatus.WORKING -> AngOrange
        AgentStatus.DONE -> Success
        AgentStatus.FAILED -> MaterialTheme.colorScheme.error
        AgentStatus.QUEUED -> Warning
        AgentStatus.IDLE -> Muted
    }

    Surface(color = Graphite, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = PanelRaised, shape = RoundedCornerShape(12.dp)) {
                Icon(icon, null, tint = InkWhite, modifier = Modifier.padding(9.dp).size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(agent.role.title, color = InkWhite, fontWeight = FontWeight.SemiBold)
                Text(agent.currentTask, color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            Box(modifier = Modifier.size(9.dp).background(stateColor, CircleShape))
        }
    }
}

@Composable
private fun RuntimeCard(health: RuntimeHealth?) {
    SectionTitle("Runtime del teléfono", health?.architecture ?: "comprobando")
    Surface(
        color = Color(0xFF0E0F12),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF24262D), RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RuntimeLine(Icons.Rounded.Terminal, "Shell", health?.detail ?: "Comprobando…")
            RuntimeLine(Icons.Rounded.Memory, "Arquitectura", health?.architecture ?: "—")
            RuntimeLine(Icons.Rounded.Storage, "Android", health?.androidVersion ?: "—")
        }
    }
}

@Composable
private fun RuntimeLine(icon: ImageVector, name: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AngOrange, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(name, color = InkWhite, modifier = Modifier.width(90.dp), fontSize = 13.sp)
        Text(value, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ProjectsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var importing by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            importing = true
            scope.launch {
                importResult = withContext(Dispatchers.IO) { WorkspaceManager(context).importTree(uri) }
                importing = false
            }
        }
    }

    ModulePage(
        title = "Proyectos",
        subtitle = "Cada proyecto vive en su propio workspace y podrá crear celdas independientes.",
        icon = Icons.Rounded.Folder
    ) {
        ActionCard(
            icon = Icons.Rounded.Add,
            title = if (importing) "Importando…" else "Importar carpeta",
            body = "Selecciona una carpeta de Android. Se copiará al workspace privado para trabajar sin tocar el original.",
            action = { if (!importing) launcher.launch(null) }
        )
        importResult?.let { result ->
            InfoCard(
                icon = if (result.success) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                title = if (result.success) "Proyecto importado" else "Importación incompleta",
                body = if (result.success) {
                    "${result.copiedFiles} archivos copiados a ${result.workspace?.name}."
                } else {
                    result.error ?: "Error desconocido"
                }
            )
        }
    }
}

@Composable
private fun ToolsScreen() {
    ModulePage(
        title = "Herramientas",
        subtitle = "Capacidades registradas detrás del Tool Broker. Ningún agente ejecuta directamente fuera de él.",
        icon = Icons.Rounded.Build
    ) {
        InfoCard(Icons.Rounded.Terminal, "process.exec", "Runner estructurado con timeout, stdout, stderr y código de salida.")
        InfoCard(Icons.Rounded.Folder, "workspace", "Importación SAF, carpetas privadas, artifacts y estado por proyecto.")
        InfoCard(Icons.Rounded.Public, "MCP / Browser", "Conectores preparados como módulos; integración completa es la siguiente capa.")
    }
}

@Composable
private fun AgentsScreen() {
    val director = remember { DirectorEngine() }
    val agents = remember { director.cellsFor(director.bootstrapPlan()) }
    ModulePage(
        title = "Agentes",
        subtitle = "Roles lógicos coordinados por un Director; no necesitamos cargar un LLM distinto por cada uno.",
        icon = Icons.Rounded.Memory
    ) {
        agents.forEach { AgentRow(it) }
    }
}

@Composable
private fun SettingsScreen() {
    val connectors = remember { ConnectorRegistry().defaults() }
    ModulePage(
        title = "Sistema",
        subtitle = "Runtime, conectores y niveles de acceso del dispositivo.",
        icon = Icons.Rounded.Settings
    ) {
        InfoCard(Icons.Rounded.Memory, "Modelo local", "Gateway desacoplado: el runtime no dependerá de una familia de modelos concreta.")
        connectors.forEach { connector ->
            InfoCard(Icons.Rounded.Public, connector.name, connector.description)
        }
    }
}

@Composable
private fun ModulePage(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = AngOrange, shape = RoundedCornerShape(14.dp)) {
                    Icon(icon, null, tint = Color.Black, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, color = InkWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, body: String, action: () -> Unit) {
    Card(
        onClick = action,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AngOrange)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = InkWhite, fontWeight = FontWeight.SemiBold)
                Text(body, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Icon(Icons.Rounded.ArrowForward, null, tint = Muted)
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String) {
    Surface(color = Graphite, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = AngOrange, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = InkWhite, fontWeight = FontWeight.SemiBold)
                Text(body, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = InkWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(trailing, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    Surface(
        color = if (active) Color(0xFF1A251F) else PanelRaised,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(7.dp).background(if (active) Success else Warning, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (active) Color(0xFFD9FFE8) else InkWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
