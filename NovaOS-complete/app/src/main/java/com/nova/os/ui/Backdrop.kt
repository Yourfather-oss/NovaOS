package com.nova.os.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.nova.os.data.WALLPAPER_FILE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val WallpaperNames: List<Pair<String, String>> = listOf(
    "aurora" to "Aurora",
    "nebula" to "Nebula",
    "midnight" to "Midnight",
    "ember" to "Ember",
    "ocean" to "Ocean",
    "forest" to "Forest",
    "rose" to "Rose",
    "mono" to "Mono"
)

private class Blob(
    val cx: Float, val cy: Float, val dx: Float, val dy: Float,
    val r: Float, val phase: Float, val color: Color
)

private class Spec(
    val top: Color,
    val bottom: Color,
    val blobs: List<Blob>,
    val stars: Boolean,
    val swatch: List<Color>
)

private fun specFor(style: String): Spec = when (style) {
    "nebula" -> Spec(
        DeepSpace, Obsidian,
        listOf(
            Blob(0.25f, 0.30f, 0.10f, 0.05f, 0.95f, 0f, Nebula.copy(alpha = 0.34f)),
            Blob(0.85f, 0.72f, 0.08f, 0.06f, 0.85f, 1.5f, Cyan.copy(alpha = 0.22f))
        ),
        true, listOf(Color(0xFF3B2F9E), Color(0xFF16345A), Color(0xFF080B12))
    )
    "midnight" -> Spec(
        Color(0xFF0E1626), Color(0xFF05070C), emptyList(), true,
        listOf(Color(0xFF16233A), Color(0xFF0B111D), Color(0xFF05070C))
    )
    "ember" -> Spec(
        Color(0xFF1C0C0C), Color(0xFF080406),
        listOf(
            Blob(0.20f, 0.20f, 0.12f, 0.05f, 0.90f, 0f, Color(0xFFFF6A3D).copy(alpha = 0.30f)),
            Blob(0.85f, 0.55f, 0.10f, 0.07f, 0.85f, 1f, Color(0xFFFFB020).copy(alpha = 0.20f)),
            Blob(0.40f, 0.95f, 0.10f, 0.00f, 0.90f, 2.5f, Color(0xFFFF3D6E).copy(alpha = 0.18f))
        ),
        false, listOf(Color(0xFF8A3418), Color(0xFF4A1620), Color(0xFF080406))
    )
    "ocean" -> Spec(
        Color(0xFF071A2A), Color(0xFF030B14),
        listOf(
            Blob(0.15f, 0.25f, 0.18f, 0.06f, 0.95f, 0f, Color(0xFF13B8D8).copy(alpha = 0.30f)),
            Blob(0.90f, 0.55f, 0.12f, 0.08f, 0.90f, 1.2f, Color(0xFF1E6BFF).copy(alpha = 0.30f)),
            Blob(0.50f, 0.95f, 0.20f, 0.03f, 0.90f, 2.4f, Color(0xFF14D3C4).copy(alpha = 0.20f))
        ),
        false, listOf(Color(0xFF0E6E8C), Color(0xFF123F86), Color(0xFF030B14))
    )
    "forest" -> Spec(
        Color(0xFF08160F), Color(0xFF040A07),
        listOf(
            Blob(0.20f, 0.20f, 0.12f, 0.05f, 0.90f, 0f, Color(0xFF2ED8A3).copy(alpha = 0.24f)),
            Blob(0.85f, 0.60f, 0.10f, 0.07f, 0.90f, 1f, Color(0xFF7BE05E).copy(alpha = 0.14f)),
            Blob(0.40f, 0.95f, 0.12f, 0.00f, 0.95f, 2.2f, Color(0xFF0E7C5A).copy(alpha = 0.30f))
        ),
        false, listOf(Color(0xFF16805F), Color(0xFF1F5A34), Color(0xFF040A07))
    )
    "rose" -> Spec(
        Color(0xFF1B0B16), Color(0xFF0A0509),
        listOf(
            Blob(0.20f, 0.20f, 0.12f, 0.05f, 0.90f, 0f, Color(0xFFFF5C8A).copy(alpha = 0.28f)),
            Blob(0.85f, 0.50f, 0.10f, 0.07f, 0.85f, 1.3f, Color(0xFF7567FF).copy(alpha = 0.24f)),
            Blob(0.45f, 0.95f, 0.10f, 0.00f, 0.90f, 2.6f, Color(0xFFFFB3C7).copy(alpha = 0.12f))
        ),
        false, listOf(Color(0xFF9E2F59), Color(0xFF47318F), Color(0xFF0A0509))
    )
    "mono" -> Spec(
        Color(0xFF131416), Color(0xFF050506),
        listOf(
            Blob(0.20f, 0.20f, 0.10f, 0.05f, 0.90f, 0f, Color.White.copy(alpha = 0.07f)),
            Blob(0.85f, 0.60f, 0.10f, 0.07f, 0.90f, 1f, Color(0xFFB0B8C4).copy(alpha = 0.09f))
        ),
        true, listOf(Color(0xFF3A3D43), Color(0xFF202226), Color(0xFF050506))
    )
    else -> Spec(
        DeepSpace, Obsidian,
        listOf(
            Blob(0.20f, 0.18f, 0.15f, 0.05f, 0.90f, 0f, Cyan.copy(alpha = 0.30f)),
            Blob(0.85f, 0.50f, 0.10f, 0.08f, 0.80f, 1f, Nebula.copy(alpha = 0.28f)),
            Blob(0.40f, 0.92f, 0.12f, 0.00f, 0.90f, 3f, Color(0xFF0FD8A8).copy(alpha = 0.16f))
        ),
        false, listOf(Color(0xFF0E5A6E), Color(0xFF3A3FA8), Color(0xFF0A1220))
    )
}

/** Colours for wallpaper and theme previews. */
fun wallpaperSwatch(style: String): List<Color> = specFor(style).swatch

/** Living wallpaper: drifting light fields over deep space, or the user's own photo. */
@Composable
fun NovaBackdrop(style: String, animate: Boolean = true, dim: Int = 35, rev: Long = 0L) {
    val context = LocalContext.current

    if (style == "custom") {
        val image by produceState<ImageBitmap?>(null, rev) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(File(context.filesDir, WALLPAPER_FILE).path)?.asImageBitmap()
                }.getOrNull()
            }
        }
        val img = image
        if (img != null) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.coerceIn(0, 80) / 100f)))
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Obsidian.copy(alpha = 0.55f)))
                    )
                )
            }
            return
        }
    }

    val spec = remember(style) { specFor(style) }
    val phase: State<Float> = if (animate) animatedPhase() else remember { mutableStateOf(0.25f) }
    val stars = remember {
        val r = Random(7)
        List(80) { Triple(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat() * 1.3f) }
    }

    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(spec.top, spec.bottom)))
        val a = (phase.value * 2.0 * PI).toFloat()
        val w = size.width
        val h = size.height

        spec.blobs.forEach { b ->
            val cx = w * (b.cx + b.dx * sin(a + b.phase))
            val cy = h * (b.cy + b.dy * cos(a + b.phase))
            val radius = w * b.r
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(b.color, Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx, cy)
            )
        }

        if (spec.stars) {
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
