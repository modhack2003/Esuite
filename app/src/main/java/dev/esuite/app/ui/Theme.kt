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
private val CyanContainer = Color(0xFF193C42)
private val Border = Color(0xFF4E6677)

@Composable
fun ESuiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Signal, onPrimary = Midnight, primaryContainer = CyanContainer, onPrimaryContainer = Signal,
        secondary = Signal, onSecondary = Midnight, secondaryContainer = CyanContainer, onSecondaryContainer = Signal,
        tertiary = Amber, onTertiary = Midnight,
        background = Midnight, surface = Raised, onSurface = Ink,
        surfaceVariant = Raised, surfaceContainer = Raised, surfaceContainerHigh = Color(0xFF172638),
        onBackground = Ink, onSurfaceVariant = Muted, outline = Border, outlineVariant = Color(0xFF304657),
    ), content = content)
}
