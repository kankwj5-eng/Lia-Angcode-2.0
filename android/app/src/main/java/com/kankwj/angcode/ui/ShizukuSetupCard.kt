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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kankwj.angcode.connectors.shizuku.ShizukuBridgeManager
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import com.kankwj.angcode.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShizukuSetupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var connecting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            refresh++
            delay(1_000)
        }
    }

    val status = remember(refresh) { ShizukuBridgeManager.status() }

    Surface(
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row {
                Icon(
                    if (status.serviceBound) Icons.Rounded.CheckCircle
                    else if (status.binderAlive) Icons.Rounded.AdminPanelSettings
                    else Icons.Rounded.Warning,
                    null,
                    tint = if (status.serviceBound) Success else AngOrange
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Android avanzado · Shizuku",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            !status.binderAlive -> "Shizuku/Sui no está ejecutándose"
                            !status.permissionGranted -> "Servicio detectado · falta autorización"
                            status.serviceBound ->
                                "Conectado · " + status.privilegeLabel +
                                    " · API " + (status.apiVersion ?: -1)
                            else -> "Autorizado · falta conectar UserService"
                        },
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            when {
                !status.binderAlive -> {
                    Text(
                        "AngCode funciona sin Shizuku. Este backend es opcional para instalar/probar APK, logcat y otras operaciones ADB/root.",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }

                !status.permissionGranted -> {
                    Button(
                        onClick = {
                            ShizukuBridgeManager.requestPermission()
                            refresh++
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngOrange,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AdminPanelSettings, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Autorizar Shizuku", fontWeight = FontWeight.Bold)
                    }
                }

                !status.serviceBound -> {
                    Button(
                        enabled = !connecting,
                        onClick = {
                            connecting = true
                            message = null
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    ShizukuBridgeManager.bind(context)
                                }
                                message = if (ok) {
                                    "✓ UserService privilegiado conectado"
                                } else {
                                    "No se pudo conectar el UserService"
                                }
                                connecting = false
                                refresh++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngOrange,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Link, null)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (connecting) "Conectando…" else "Conectar herramientas Android",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                else -> {
                    OutlinedButton(
                        onClick = {
                            refresh++
                            message = "Backend listo para misiones Android"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Backend listo", fontSize = 11.sp)
                    }
                }
            }

            message?.let {
                Text(
                    it,
                    color = if (it.startsWith("✓")) Success else Muted,
                    fontSize = 10.sp
                )
            }

            Text(
                "El permiso se concede en Shizuku. AngCode no habilita estas capacidades automáticamente para todos los agentes.",
                color = Muted,
                fontSize = 9.sp
            )
        }
    }
}
