package com.jordanmind.agent.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Accent hues used for gradients, glows and the assistant orb. */
object Accent {
    val Gold = Color(0xFFFFC91F)
    val Amber = Color(0xFFFF9E2C)
    val Ember = Color(0xFFFF6B35)
    val Lime = Color(0xFFCDDC39)
    val Aqua = Color(0xFF35D0BA)

    /** Warm sweep used on primary actions and the active nav chip. */
    val Warm = listOf(Gold, Amber, Ember)
    val Cool = listOf(Aqua, Lime)
}

/**
 * The signature background: a near-black canvas lit by two soft accent blooms. Drawn rather than
 * shipped as an image so it costs nothing in APK size and scales to any screen.
 *
 * The drift only runs while the screen is actually visible. An infinite animation is never
 * considered idle, so leaving it on would keep the whole UI recomposing and the GPU busy in the
 * background — on a Helio G81 that is measurable battery drain and heat for something nobody can
 * see. [reduceMotion] pins the drift still for low-end devices while keeping the gradient.
 */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier, reduceMotion: Boolean = false) {
    var visible by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            visible = event == Lifecycle.Event.ON_START
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val animate = visible && !reduceMotion
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    val shift = if (animate) drift else 0.5f

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0C0A05), Color(0xFF120F08), Color(0xFF0A0906)),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Accent.Amber.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(w * (0.12f + 0.10f * shift), h * 0.04f),
                        radius = w * 0.85f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Accent.Aqua.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(w * (0.95f - 0.12f * shift), h * 0.72f),
                        radius = w * 0.75f,
                    ),
                )
            },
    )
}

/** Hairline gradient border that makes a panel read as lit glass rather than a flat rectangle. */
@Composable
fun GradientBorderBox(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 18,
    colors: List<Color> = listOf(Accent.Amber.copy(alpha = 0.45f), Color.Transparent),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .background(Color(0xFF16130B), RoundedCornerShape(cornerRadius.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(colors),
                shape = RoundedCornerShape(cornerRadius.dp),
            ),
    ) { content() }
}

/**
 * Pulsing concentric rings used as the assistant avatar. Rotates so it reads as alive while the
 * model is decoding, which is the only feedback available during a long generation.
 */
@Composable
fun AssistantOrb(size: Int = 44, active: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "orb")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 5_000 else 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )
    Box(
        Modifier
            .rotate(spin)
            .drawBehind {
                val r = size / 2f
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Accent.Gold.copy(alpha = 0.95f),
                            Accent.Ember.copy(alpha = 0.85f),
                            Accent.Aqua.copy(alpha = 0.75f),
                            Accent.Gold.copy(alpha = 0.95f),
                        ),
                    ),
                    radius = r,
                )
                drawCircle(Color(0xFF0C0A05), radius = r * 0.62f)
                drawCircle(Accent.Gold.copy(alpha = 0.9f), radius = r * 0.26f)
            },
    )
}
