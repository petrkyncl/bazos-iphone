package cz.petrkyncl.bazosiphony.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Významové barvy (zisk/ztráta/varování) — stejné bez ohledu na barvy z tapety. */
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
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF5B8FF9))
        else -> lightColorScheme(primary = Color(0xFF2F6FED))
    }
    CompositionLocalProvider(LocalStatus provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
