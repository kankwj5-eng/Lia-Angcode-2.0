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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.runtime.ActiveProjectStore
import com.kankwj.angcode.runtime.WorkspaceManager
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import com.kankwj.angcode.ui.theme.Success

@Composable
fun ProjectWorkspacePanel() {
    val context = LocalContext.current
    val manager = remember { WorkspaceManager(context) }
    val store = remember { ActiveProjectStore(context, manager) }
    var refresh by remember { mutableIntStateOf(0) }

    val active = remember(refresh) { store.active() }
    val projects = remember(refresh) { manager.listWorkspaces() }

    Surface(
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row {
                Icon(Icons.Rounded.Folder, null, tint = AngOrange)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Workspaces",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        active?.let { "Activo: " + it.name } ?: "Sin proyecto activo",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            if (projects.isEmpty()) {
                Text(
                    "Importa una carpeta para crear tu primer workspace.",
                    color = Muted,
                    fontSize = 11.sp
                )
            } else {
                projects.forEach { project ->
                    val selected = active?.canonicalPath == project.canonicalPath
                    Surface(
                        color = Graphite,
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(11.dp)
                        ) {
                            Icon(
                                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Folder,
                                null,
                                tint = if (selected) Success else AngOrange
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    project.name,
                                    color = InkWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    if (selected) "Proyecto activo" else "Workspace local",
                                    color = Muted,
                                    fontSize = 9.sp
                                )
                            }
                            if (!selected) {
                                OutlinedButton(
                                    onClick = {
                                        store.setActive(project)
                                        refresh++
                                    }
                                ) {
                                    Text("Usar", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
