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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.connectors.root.RootBridgeManager
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import com.kankwj.angcode.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RootSetupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(RootBridgeManager.status(context)) }
    var busy by remember { mutableStateOf(false) }

    Surface(
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row {
                Icon(
                    if (status.root) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                    null,
                    tint = if (status.root) Success else AngOrange
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Root opcional",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        status.detail,
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
                Icon(Icons.Rounded.Lock, null, tint = Muted)
            }

            if (!status.root) {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            status = withContext(Dispatchers.IO) {
                                RootBridgeManager.requestRoot(context)
                            }
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngOrange,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (busy) "Comprobando…" else "Comprobar / solicitar root",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = {
                        RootBridgeManager.disable(context)
                        status = RootBridgeManager.status(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Desactivar root en AngCode")
                }
            }

            Row {
                Icon(Icons.Rounded.Warning, null, tint = AngOrange)
                Spacer(Modifier.width(7.dp))
                Text(
                    "Root nunca es requisito. Las herramientas no pueden solicitarlo por sí solas y no existe root.shell. Cada misión debe aprobar ROOT_PRIVILEGED aparte.",
                    color = Muted,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
