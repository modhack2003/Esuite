package dev.esuite.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Midnight = Color(0xFF071019)
val Raised = Color(0xFF101C29)
val Signal = Color(0xFF5BE0D6)
val Amber = Color(0xFFFFB86B)
val Ink = Color(0xFFE7EEF6)
val Muted = Color(0xFF92A5B8)

@Composable
fun ESuiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Signal, onPrimary = Midnight, secondary = Amber,
        background = Midnight, surface = Raised, onSurface = Ink,
        onBackground = Ink, onSurfaceVariant = Muted,
    ), content = content)
}
