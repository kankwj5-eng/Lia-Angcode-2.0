package com.kankwj.angcode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Obsidian = Color(0xFF09090B)
val Graphite = Color(0xFF121317)
val Panel = Color(0xFF18191E)
val PanelRaised = Color(0xFF202127)
val AngOrange = Color(0xFFFF7A00)
val AngOrangeSoft = Color(0xFFFFA24B)
val InkWhite = Color(0xFFF7F7F8)
val Muted = Color(0xFF9EA2AA)
val Success = Color(0xFF58D68D)
val Warning = Color(0xFFFFC857)

private val AngCodeColors = darkColorScheme(
    primary = AngOrange,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A1C00),
    onPrimaryContainer = Color(0xFFFFD1A3),
    secondary = AngOrangeSoft,
    background = Obsidian,
    onBackground = InkWhite,
    surface = Graphite,
    onSurface = InkWhite,
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    outline = Color(0xFF30323A),
    error = Color(0xFFFF6B6B)
)

@Composable
fun AngCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AngCodeColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
