package cz.petrkyncl.bazosiphony.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Bazošova oranžová (bazos.cz: #ff6600, tmavší #bf4c00, světlá #ffd9bf). */
val BazosOrange = Color(0xFFFF6600)

private val Light = lightColorScheme(
    primary = BazosOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF0E6),
    onPrimaryContainer = Color(0xFF4A1F00),
    secondary = Color(0xFFBF4C00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF0E6),
    onSecondaryContainer = Color(0xFF4A1F00),
    background = Color(0xFFF5F5F6),
    surface = Color(0xFFF5F5F6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEEEEF0),
    surfaceContainerHigh = Color(0xFFE8E8EA),
    surfaceContainerHighest = Color(0xFFE1E1E4),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFDCDCE0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A3D),
    onPrimary = Color(0xFF2B1000),
    primaryContainer = Color(0xFF2E2118),
    onPrimaryContainer = Color(0xFFFFD9C2),
    secondary = Color(0xFFFFB68C),
    onSecondary = Color(0xFF4A1F00),
    secondaryContainer = Color(0xFF2E2118),
    onSecondaryContainer = Color(0xFFFFD9C2),
    background = Color(0xFF111113),
    surface = Color(0xFF111113),
    surfaceContainerLowest = Color(0xFF0C0C0E),
    surfaceContainerLow = Color(0xFF1A1A1D),
    surfaceContainer = Color(0xFF1F1F23),
    surfaceContainerHigh = Color(0xFF29292D),
    surfaceContainerHighest = Color(0xFF333338),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF3A3A3F),
)

/** Významové barvy (zisk/ztráta/varování). */
@Immutable
data class StatusColors(
    val good: Color, val goodBg: Color,
    val bad: Color, val badBg: Color,
    val warn: Color, val warnBg: Color,
    val info: Color, val infoBg: Color,
)

private val LightStatus = StatusColors(
    good = Color(0xFF14804A), goodBg = Color(0xFFE3F5EB),
    bad = Color(0xFFC4321F), badBg = Color(0xFFFBE9E6),
    warn = Color(0xFFA35F00), warnBg = Color(0xFFFDF1DC),
    info = Color(0xFF2D5FB8), infoBg = Color(0xFFE7EEFB),
)
private val DarkStatus = StatusColors(
    good = Color(0xFF4CC98A), goodBg = Color(0xFF133324),
    bad = Color(0xFFFF7A66), badBg = Color(0xFF3A1A16),
    warn = Color(0xFFF0B04A), warnBg = Color(0xFF36280F),
    info = Color(0xFF85AAF5), infoBg = Color(0xFF1A2640),
)

val LocalStatus = staticCompositionLocalOf { LightStatus }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalStatus provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
