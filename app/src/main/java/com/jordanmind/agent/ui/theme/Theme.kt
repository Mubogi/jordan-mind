package com.jordanmind.agent.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Amber ramp. Yellow on dark needs the container tones to stay desaturated, otherwise large
// filled surfaces vibrate against the near-black background.
private val Ink = Color(0xFF0C0A05)
private val Surface1 = Color(0xFF16130B)
private val Surface2 = Color(0xFF211C11)
private val Surface3 = Color(0xFF2C2517)

private val Gold = Color(0xFFFFC91F)
private val GoldDeep = Color(0xFF6B4E00)
private val GoldSoft = Color(0xFFFFE08A)
private val Amber = Color(0xFFFF9E2C)
private val Lime = Color(0xFFCDDC39)

/**
 * Jordan Mind is dark-only by design. There is deliberately no light scheme and no
 * isSystemInDarkTheme() branch — a half-finished light theme looks worse than none.
 */
private val JmColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF241A00),
    primaryContainer = GoldDeep,
    onPrimaryContainer = GoldSoft,
    secondary = Amber,
    onSecondary = Color(0xFF2A1400),
    secondaryContainer = Color(0xFF6B3A00),
    onSecondaryContainer = Color(0xFFFFDCBE),
    tertiary = Lime,
    onTertiary = Color(0xFF1D2000),
    background = Ink,
    onBackground = Color(0xFFF3EEE2),
    surface = Surface1,
    onSurface = Color(0xFFF3EEE2),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFBFB49B),
    surfaceContainerHighest = Surface3,
    outline = Color(0xFF4A4130),
    outlineVariant = Color(0xFF302A1D),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A0A),
)

private val JmTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.02).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        letterSpacing = (-0.01).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.08.sp,
    ),
)

private val JmShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun JordanMindTheme(content: @Composable () -> Unit) {
    val view = LocalContext.current
    SideEffect {
        (view as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = JmColors,
        typography = JmTypography,
        shapes = JmShapes,
        content = content,
    )
}
