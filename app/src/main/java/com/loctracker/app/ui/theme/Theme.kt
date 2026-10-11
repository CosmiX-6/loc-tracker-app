package com.loctracker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B0F14)
val Panel = Color(0xFF121923)
val PanelHigh = Color(0xFF1A2330)
val Accent = Color(0xFF2DD4A7)
val Info = Color(0xFF5B8DEF)
val Warn = Color(0xFFFFB84D)
val Danger = Color(0xFFFF6B6B)
val Muted = Color(0xFF8B98A9)

private val Scheme = darkColorScheme(
    primary = Accent, onPrimary = Ink,
    secondary = Info, onSecondary = Ink,
    background = Ink, onBackground = Color(0xFFE6EBF2),
    surface = Panel, onSurface = Color(0xFFE6EBF2),
    surfaceVariant = PanelHigh, onSurfaceVariant = Muted,
    surfaceContainer = Panel, surfaceContainerHigh = PanelHigh,
    error = Danger, onError = Ink,
    outline = Color(0xFF2A3647), outlineVariant = Color(0xFF1F2A39),
)

/** The app is intentionally dark-only: a map-first dashboard that is easy on the eyes at night. */
@Composable
fun LocTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
