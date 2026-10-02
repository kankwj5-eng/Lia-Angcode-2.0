package com.kankwj.angcode.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.kankwj.angcode.ui.theme.AngOrange
import com.kankwj.angcode.ui.theme.Graphite
import com.kankwj.angcode.ui.theme.InkWhite
import com.kankwj.angcode.ui.theme.Obsidian

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen() {
    var address by remember { mutableStateOf("https://github.com") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Obsidian).statusBarsPadding()
    ) {
        Surface(
            color = Graphite,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { webViewRef?.goBack() }, enabled = webViewRef?.canGoBack() == true) {
                        Icon(Icons.Rounded.ArrowBack, "Atrás", tint = InkWhite)
                    }
                    IconButton(onClick = { webViewRef?.goForward() }, enabled = webViewRef?.canGoForward() == true) {
                        Icon(Icons.Rounded.ArrowForward, "Adelante", tint = InkWhite)
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Rounded.Refresh, "Recargar", tint = AngOrange)
                    }
                    Text(
                        "Navegador de trabajo",
                        color = InkWhite,
                        modifier = Modifier.padding(top = 13.dp)
                    )
                }

                BrowserAgentBackendCard(
                    onTakeControl = { url ->
                        address = url
                        webViewRef?.loadUrl(url)
                    }
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AngOrange) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val normalized = if (address.startsWith("http://") || address.startsWith("https://")) {
                                address
                            } else {
                                "https://$address"
                            }
                            address = normalized
                            webViewRef?.loadUrl(normalized)
                        }) {
                            Icon(Icons.Rounded.ArrowForward, "Abrir", tint = AngOrange)
                        }
                    }
                )
            }
        }

        LocalPreviewCard(
            onOpen = { url ->
                address = url
                webViewRef?.loadUrl(url)
            }
        )

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.rgb(9, 9, 11))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(false)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val scheme = request?.url?.scheme?.lowercase()
                            return scheme != "http" && scheme != "https"
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (!url.isNullOrBlank()) address = url
                        }
                    }
                    webViewRef = this
                    loadUrl(address)
                }
            },
            update = { webViewRef = it }
        )
    }
}
