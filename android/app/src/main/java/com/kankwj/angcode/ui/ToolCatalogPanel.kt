package com.kankwj.angcode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.runtime.CapabilityStatus
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCatalog
import com.kankwj.angcode.runtime.ToolCapability
import com.kankwj.angcode.runtime.ToolFamily
import com.kankwj.angcode.runtime.registerAndroidTools
import com.kankwj.angcode.runtime.registerCoreTools
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.PanelRaised
import com.kankwj.angcode.ui.theme.Success
import com.kankwj.angcode.ui.theme.Warning

@Composable
fun ToolCatalogPanel() {
    val context = LocalContext.current
    val broker = remember {
        ToolBroker()
            .registerCoreTools()
            .registerAndroidTools(context)
    }
    val registered = remember(broker) { broker.availableTools().map { it.id }.toSet() }
    val grouped = remember { ToolCatalog.capabilities.groupBy { it.family } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolSummary(
            registeredCount = registered.size,
            catalogCount = ToolCatalog.capabilities.size
        )

        ToolFamily.entries.forEach { family ->
            val tools = grouped[family].orEmpty()
            if (tools.isNotEmpty()) {
                ToolFamilyCard(family, tools, registered)
            }
        }
    }
}

@Composable
private fun ToolSummary(registeredCount: Int, catalogCount: Int) {
    Surface(
        color = Color(0xFF111215),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "Tool Broker",
                color = InkWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$registeredCount herramientas registradas · $catalogCount capacidades catalogadas",
                color = Muted,
                fontSize = 12.sp
            )
            Text(
                "Las capacidades opcionales aparecen aunque su backend todavía no esté instalado. Así el Director puede saber qué puede activar después.",
                color = Color(0xFFD7D9DE),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun ToolFamilyCard(
    family: ToolFamily,
    tools: List<ToolCapability>,
    registered: Set<String>
) {
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    familyLabel(family),
                    color = InkWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    tools.size.toString(),
                    color = AngOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            tools.forEach { capability ->
                ToolCapabilityRow(capability, isRegistered(capability.id, registered))
            }
        }
    }
}

@Composable
private fun ToolCapabilityRow(capability: ToolCapability, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .background(statusColor(capability.status, active), CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                capability.id,
                color = InkWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                capability.description,
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            color = PanelRaised,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                statusLabel(capability.status, active),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = statusColor(capability.status, active),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun isRegistered(id: String, registered: Set<String>): Boolean {
    if (!id.contains("*")) return id in registered
    val prefix = id.substringBefore("*")
    return registered.any { it.startsWith(prefix) }
}

private fun statusLabel(status: CapabilityStatus, active: Boolean): String =
    when {
        active -> "ACTIVA"
        status == CapabilityStatus.BUILT_IN -> "BASE"
        status == CapabilityStatus.READY_TO_ADAPT -> "PACK"
        status == CapabilityStatus.OPTIONAL_BACKEND -> "OPCIONAL"
        else -> "PLAN"
    }

private fun statusColor(status: CapabilityStatus, active: Boolean): Color =
    when {
        active -> Success
        status == CapabilityStatus.BUILT_IN -> AngOrange
        status == CapabilityStatus.READY_TO_ADAPT -> Warning
        status == CapabilityStatus.OPTIONAL_BACKEND -> Color(0xFF78A9FF)
        else -> Muted
    }

private fun familyLabel(family: ToolFamily): String =
    when (family) {
        ToolFamily.FILES -> "Archivos y workspace"
        ToolFamily.PROCESS -> "Procesos"
        ToolFamily.NETWORK -> "Red"
        ToolFamily.ARCHIVE -> "Artefactos y checkpoints"
        ToolFamily.VERSION_CONTROL -> "Git / versiones"
        ToolFamily.CODE_INTELLIGENCE -> "Código"
        ToolFamily.ANDROID -> "Android"
        ToolFamily.SANDBOX -> "Sandbox / Linux"
        ToolFamily.CONNECTOR -> "Conectores"
    }
