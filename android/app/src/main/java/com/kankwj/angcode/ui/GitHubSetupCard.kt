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
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kankwj.angcode.connectors.GitHubApiClient
import com.kankwj.angcode.connectors.GitHubTokenStore
import com.kankwj.angcode.connectors.GitHubUser
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Muted
import com.kankwj.angcode.ui.theme.Panel
import com.kankwj.angcode.ui.theme.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GitHubSetupCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { GitHubTokenStore(context) }
    val client = remember { GitHubApiClient(context) }

    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var user by remember { mutableStateOf<GitHubUser?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh) {
        if (!store.hasToken()) {
            user = null
            return@LaunchedEffect
        }

        val result = withContext(Dispatchers.IO) {
            runCatching { client.currentUser() }
        }
        result.onSuccess {
            user = it
            message = null
        }.onFailure {
            user = null
            message = "Token guardado, pero GitHub respondió: " +
                (it.message ?: "error desconocido")
        }
    }

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
                    if (user != null) Icons.Rounded.CheckCircle else Icons.Rounded.Link,
                    null,
                    tint = if (user != null) Success else AngOrange
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "GitHub",
                        color = InkWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        user?.let { "Conectado como @" + it.login }
                            ?: "Conector REST de solo lectura",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
                Icon(Icons.Rounded.Lock, null, tint = Muted)
            }

            if (user == null) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.take(512) },
                    label = { Text("Personal access token") },
                    placeholder = { Text("github_pat_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = !busy && token.trim().length >= 20,
                    onClick = {
                        val candidate = token.trim()
                        busy = true
                        message = null

                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    store.save(candidate)
                                    val current = client.currentUser()
                                    current
                                }
                            }

                            result.onSuccess {
                                token = ""
                                user = it
                                message = "✓ GitHub conectado"
                            }.onFailure {
                                store.clear()
                                user = null
                                message = "✕ " + (it.message ?: "Token rechazado")
                            }
                            busy = false
                            refresh++
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
                        if (busy) "Verificando…" else "Guardar y verificar",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        store.clear()
                        token = ""
                        user = null
                        message = "GitHub desconectado"
                        refresh++
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Desconectar GitHub")
                }
            }

            message?.let {
                Row {
                    Icon(
                        if (it.startsWith("✓")) Icons.Rounded.CheckCircle
                        else Icons.Rounded.Warning,
                        null,
                        tint = if (it.startsWith("✓")) Success else AngOrange
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        it,
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }

            Text(
                "El token se cifra con Android Keystore. Los agentes nunca reciben su valor; solo llaman herramientas github.*.",
                color = Muted,
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
        }
    }
}
