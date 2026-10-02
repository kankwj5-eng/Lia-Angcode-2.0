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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.runtime.ActiveProjectStore
import com.kankwj.angcode.runtime.ToolAuditLog
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel

@Composable
fun ToolAuditPanel() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val workspace = remember(refresh) {
        ActiveProjectStore(context).active()
    }
    val events = remember(refresh, workspace) {
        workspace?.let { ToolAuditLog.tail(it, 12) }.orEmpty()
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
                Icon(Icons.Rounded.History, null, tint = AngOrange)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Auditoría de herramientas",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        workspace?.name ?: "Sin proyecto activo",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
                IconButton(onClick = { refresh++ }) {
                    Icon(Icons.Rounded.Refresh, "Actualizar", tint = AngOrange)
                }
            }

            if (events.isEmpty()) {
                Text(
                    "Aún no hay llamadas registradas en este proyecto.",
                    color = Muted,
                    fontSize = 10.sp
                )
            } else {
                Surface(
                    color = Graphite,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        events.joinToString("\n") { it.take(700) },
                        color = InkWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        lineHeight = 11.sp,
                        modifier = Modifier.padding(11.dp)
                    )
                }
            }

            Text(
                "Argumentos sensibles se redactan antes de escribirse en el historial.",
                color = Muted,
                fontSize = 9.sp
            )
        }
    }
}
