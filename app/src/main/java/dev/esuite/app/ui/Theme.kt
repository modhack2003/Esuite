package dev.esuite.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal enum class Appearance(val label: String) { System("System"), Light("Light"), Dark("Dark") }

private val deepNavy = Color(0xFF071019)
private val navySurface = Color(0xFF101C29)
private val turquoise = Color(0xFF64E2D7)
private val deepTeal = Color(0xFF006A62)
private val paleMint = Color(0xFFC0F3EB)

private val darkScheme = darkColorScheme(
    primary = turquoise, onPrimary = deepNavy,
    primaryContainer = Color(0xFF173D42), onPrimaryContainer = turquoise,
    secondary = turquoise, onSecondary = deepNavy,
    secondaryContainer = Color(0xFF193C42), onSecondaryContainer = turquoise,
    tertiary = Color(0xFFFFBD79), onTertiary = deepNavy,
    background = deepNavy, onBackground = Color(0xFFE7EEF6),
    surface = navySurface, onSurface = Color(0xFFE7EEF6),
    surfaceVariant = Color(0xFF213142), onSurfaceVariant = Color(0xFFA7B9C9),
    surfaceContainer = navySurface, surfaceContainerHigh = Color(0xFF172638),
    outline = Color(0xFF5B7388), outlineVariant = Color(0xFF34495D),
)

private val lightScheme = lightColorScheme(
    primary = deepTeal, onPrimary = Color.White,
    primaryContainer = paleMint, onPrimaryContainer = Color(0xFF004F48),
    secondary = deepTeal, onSecondary = Color.White,
    secondaryContainer = paleMint, onSecondaryContainer = Color(0xFF004F48),
    tertiary = Color(0xFF8B4508),
    background = Color(0xFFF5F8FA), onBackground = Color(0xFF142231),
    surface = Color.White, onSurface = Color(0xFF142231),
    surfaceVariant = Color(0xFFE5EEF2), onSurfaceVariant = Color(0xFF465D70),
    surfaceContainer = Color(0xFFEDF3F6), surfaceContainerHigh = Color(0xFFE7EFF3),
    outline = Color(0xFF647D90), outlineVariant = Color(0xFFC6D5DF),
)

val Midnight: Color @Composable get() = MaterialTheme.colorScheme.background
val Raised: Color @Composable get() = MaterialTheme.colorScheme.surface
val Signal: Color @Composable get() = MaterialTheme.colorScheme.primary
val Amber: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
internal fun isDark(appearance: Appearance): Boolean = when (appearance) {
    Appearance.System -> isSystemInDarkTheme()
    Appearance.Dark -> true
    Appearance.Light -> false
}

@Composable
internal fun ESuiteTheme(appearance: Appearance, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark(appearance)) darkScheme else lightScheme, content = content)
}
