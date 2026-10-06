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
    primaryContainer = Color(0xFFFFD9BF),
    onPrimaryContainer = Color(0xFF3A1600),
    secondary = Color(0xFFBF4C00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFECDD),
    onSecondaryContainer = Color(0xFF4A1F00),
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFFFFBF8),
    surface = Color(0xFFFFFBF8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFBF3EC),
    surfaceContainerHigh = Color(0xFFF6ECE4),
    surfaceContainerHighest = Color(0xFFF0E5DC),
    outline = Color(0xFF9C8B80),
    outlineVariant = Color(0xFFE6D6CA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A3D),
    onPrimary = Color(0xFF3A1600),
    primaryContainer = Color(0xFF8A3700),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFFFB68C),
    onSecondary = Color(0xFF4A1F00),
    secondaryContainer = Color(0xFF5E2A05),
    onSecondaryContainer = Color(0xFFFFDBC8),
    background = Color(0xFF16110E),
    surface = Color(0xFF16110E),
    surfaceContainerLowest = Color(0xFF110C09),
    surfaceContainerLow = Color(0xFF1F1915),
    surfaceContainer = Color(0xFF241D18),
    surfaceContainerHigh = Color(0xFF2E2722),
    surfaceContainerHighest = Color(0xFF39312C),
    outline = Color(0xFF9F8D82),
    outlineVariant = Color(0xFF52443B),
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
