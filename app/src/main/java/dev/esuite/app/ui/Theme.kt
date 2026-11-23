package dev.esuite.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.esuite.app.R

internal enum class Appearance(val label: String) { System("System"), Light("Light"), Dark("Dark") }

private val darkScheme = darkColorScheme(
    primary = Color(0xFFACC3FF), onPrimary = Color(0xFF072753),
    primaryContainer = Color(0xFF293F68), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFBBC7DD), onSecondary = Color(0xFF263245),
    secondaryContainer = Color(0xFF2A364A), onSecondaryContainer = Color(0xFFDEE8FA),
    tertiary = Color(0xFFF4C683), error = Color(0xFFFFB4AE),
    background = Color(0xFF101318), onBackground = Color(0xFFF1F3F7),
    surface = Color(0xFF181C23), onSurface = Color(0xFFF1F3F7),
    surfaceVariant = Color(0xFF252C37), onSurfaceVariant = Color(0xFFB4BECD),
    surfaceContainerLow = Color(0xFF14181E), surfaceContainer = Color(0xFF1D222B),
    surfaceContainerHigh = Color(0xFF252B35), surfaceContainerHighest = Color(0xFF2F3743),
    outline = Color(0xFF818D9F), outlineVariant = Color(0xFF394352),
)

private val lightScheme = lightColorScheme(
    primary = Color(0xFF254D94), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF), onPrimaryContainer = Color(0xFF18396F),
    secondary = Color(0xFF465773), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EAF6), onSecondaryContainer = Color(0xFF2B3D5A),
    tertiary = Color(0xFF805100), error = Color(0xFFA32D29),
    background = Color(0xFFF3F5F8), onBackground = Color(0xFF171C25),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF171C25),
    surfaceVariant = Color(0xFFE8EDF5), onSurfaceVariant = Color(0xFF4A576A),
    surfaceContainerLow = Color(0xFFF8FAFD), surfaceContainer = Color(0xFFEDF1F7),
    surfaceContainerHigh = Color(0xFFE6ECF5), surfaceContainerHighest = Color(0xFFDCE5F1),
    outline = Color(0xFF6C798C), outlineVariant = Color(0xFFC8D1DF),
)

@OptIn(ExperimentalTextApi::class)
private val inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val defaults = Typography()
private val typography = Typography(
    headlineMedium = defaults.headlineMedium.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.45).sp),
    titleLarge = defaults.titleLarge.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.35).sp),
    titleMedium = defaults.titleMedium.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = defaults.titleSmall.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = inter, letterSpacing = 0.sp),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = inter, letterSpacing = 0.sp),
    bodySmall = defaults.bodySmall.copy(fontFamily = inter, letterSpacing = 0.sp),
    labelLarge = defaults.labelLarge.copy(fontFamily = inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelMedium = defaults.labelMedium.copy(fontFamily = inter, letterSpacing = 0.sp),
    labelSmall = defaults.labelSmall.copy(fontFamily = inter, letterSpacing = 0.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(5.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp),
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
    MaterialTheme(colorScheme = if (isDark(appearance)) darkScheme else lightScheme,
        typography = typography, shapes = shapes, content = content)
}
