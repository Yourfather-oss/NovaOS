package com.nova.os.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Animated living wallpaper: drifting light fields over deep space. */
@Composable
fun NovaBackdrop(style: String, animate: Boolean = true) {
    val phase: State<Float> = if (animate) animatedPhase() else remember { mutableStateOf(0.25f) }
    val stars = remember {
        val r = Random(7)
        List(80) { Triple(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat() * 1.3f) }
    }

    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(DeepSpace, Obsidian)))
        val a = (phase.value * 2.0 * PI).toFloat()
        val w = size.width
        val h = size.height

        fun blob(cx: Float, cy: Float, radius: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx, cy)
            )
        }

        when (style) {
            "nebula" -> {
                blob(w * (0.25f + 0.10f * sin(a)), h * (0.30f + 0.05f * cos(a)), w * 0.95f, Nebula.copy(alpha = 0.34f))
                blob(w * (0.85f + 0.08f * cos(a)), h * (0.72f + 0.06f * sin(a)), w * 0.85f, Cyan.copy(alpha = 0.22f))
            }
            "midnight" -> { /* stars only */ }
            else -> {
                blob(w * (0.20f + 0.15f * sin(a)), h * (0.18f + 0.05f * cos(a)), w * 0.90f, Cyan.copy(alpha = 0.30f))
                blob(w * (0.85f + 0.10f * cos(a + 1f)), h * (0.50f + 0.08f * sin(a + 2f)), w * 0.80f, Nebula.copy(alpha = 0.28f))
                blob(w * (0.40f + 0.12f * sin(a + 3f)), h * 0.92f, w * 0.90f, Color(0xFF0FD8A8).copy(alpha = 0.16f))
            }
        }

        if (style != "aurora") {
            stars.forEachIndexed { i, (x, y, r) ->
                val twinkle = 0.25f + 0.55f * abs(sin(a * 2f + i))
                drawCircle(Color.White.copy(alpha = twinkle), radius = r, center = Offset(x * w, y * h))
            }
        }
    }
}

@Composable
private fun animatedPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "backdrop")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
}
