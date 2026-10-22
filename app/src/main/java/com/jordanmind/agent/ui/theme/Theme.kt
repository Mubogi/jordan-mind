package com.jordanmind.agent.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Ink = Color(0xFF0B0F14)
private val Surface1 = Color(0xFF121821)
private val Surface2 = Color(0xFF1A2230)
private val Teal = Color(0xFF5EEAD4)
private val TealDim = Color(0xFF0F766E)
private val Violet = Color(0xFFA78BFA)
private val Amber = Color(0xFFFBBF24)

val JmColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF04231F),
    primaryContainer = TealDim,
    onPrimaryContainer = Color(0xFFCFFAFE),
    secondary = Violet,
    onSecondary = Color(0xFF1E1B4B),
    tertiary = Amber,
    background = Ink,
    onBackground = Color(0xFFE5EAF2),
    surface = Surface1,
    onSurface = Color(0xFFE5EAF2),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFA9B4C4),
    outline = Color(0xFF374151),
    error = Color(0xFFF87171),
)

val JmTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
    ),
)

@Composable
fun JordanMindTheme(
    // The app is designed dark-first; the toggle only exists for accessibility.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalContext.current
    SideEffect {
        (view as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = JmColors,
        typography = JmTypography,
        content = content,
    )
}
