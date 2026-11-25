package dev.droidnix.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green = Color(0xFF7CE38B)
private val Blue = Color(0xFF8AB4F8)
private val DarkBg = Color(0xFF0B1020)

private val FallbackDark = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF14521F),
    onPrimaryContainer = Color(0xFFB8F5C0),
    secondary = Blue,
    onSecondary = Color(0xFF10233F),
    secondaryContainer = Color(0xFF1D3557),
    onSecondaryContainer = Color(0xFFD3E3FF),
    background = DarkBg,
    onBackground = Color(0xFFE4E8F1),
    surface = Color(0xFF121829),
    onSurface = Color(0xFFE4E8F1),
    surfaceVariant = Color(0xFF243047),
    onSurfaceVariant = Color(0xFFC2CADB),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF8D97AB),
)

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF1D6B2C),
    secondary = Color(0xFF3A5F8F),
    background = Color(0xFFF7FAF7),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun DroidNixTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = remember(dark, context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) FallbackDark else FallbackLight
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
