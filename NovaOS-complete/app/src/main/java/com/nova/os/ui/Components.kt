package com.nova.os.ui

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.data.AppInfo
import kotlin.math.abs

/** Springy press feedback with haptics on long-press. */
fun Modifier.bounceClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "press"
    )
    val haptic = LocalHapticFeedback.current
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { click() },
                onLongPress = {
                    long?.let {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                }
            )
        }
}

/** Stops touches from falling through to the layer underneath. */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }

private object HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.25f)
            lineTo(w, h * 0.75f)
            lineTo(w * 0.5f, h)
            lineTo(0f, h * 0.75f)
            lineTo(0f, h * 0.25f)
            close()
        }
        return Outline.Generic(path)
    }
}

internal fun iconShapeFor(shape: String, size: Dp): Shape = when (shape) {
    "circle" -> CircleShape
    "rounded" -> RoundedCornerShape(size * 0.18f)
    "teardrop" -> RoundedCornerShape(
        topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 50, bottomStartPercent = 12
    )
    "hexagon" -> HexagonShape
    "original" -> RoundedCornerShape(0.dp)
    else -> RoundedCornerShape(size * 0.3f)
}

/**
 * App icon in the chosen shape and style: glass, dark tile, neon, mono glass or plain.
 * The style follows the Design Studio unless [finish] is given.
 */
@Composable
fun NovaIcon(
    app: AppInfo,
    size: Dp,
    shape: String,
    modifier: Modifier = Modifier,
    finish: String? = null
) {
    val style = finish ?: LocalIconFinish.current
    val accent = LocalAccent.current
    val outer = iconShapeFor(shape, size)

    if (shape == "original") {
        Image(
            bitmap = app.icon, contentDescription = app.label,
            modifier = modifier.size(size), contentScale = ContentScale.Crop
        )
        return
    }
    if (style == "plain") {
        Image(
            bitmap = app.icon, contentDescription = app.label,
            modifier = modifier
                .size(size)
                .shadow(8.dp, outer, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(outer)
                .border(1.dp, Brush.linearGradient(listOf(Color(0x77FFFFFF), Color(0x11FFFFFF))), outer),
            contentScale = ContentScale.Crop
        )
        return
    }

    val inner = iconShapeFor(shape, size * 0.74f)
    val glassy = style == "glass" || style == "mono"
    val neon = style == "neon"
    val fill: Brush = when (style) {
        "tile" -> Brush.verticalGradient(listOf(Color(0xFF223049), Color(0xFF131C2C)))
        "neon" -> Brush.verticalGradient(listOf(Color(0xFF0C1424), Color(0xFF070C16)))
        else -> Brush.linearGradient(listOf(Color(0x59FFFFFF), Color(0x14FFFFFF), Color(0x2EFFFFFF)))
    }
    val rim: Brush = when (style) {
        "neon" -> Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f), accent))
        "tile" -> Brush.linearGradient(listOf(Color(0x44FFFFFF), Color(0x11FFFFFF)))
        else -> Brush.linearGradient(listOf(Color(0xCCFFFFFF), Color(0x1AFFFFFF), Color(0x66FFFFFF)))
    }
    val glow = if (neon) accent else Color.Black
    Box(
        modifier
            .size(size)
            .shadow(if (neon) 14.dp else 10.dp, outer, ambientColor = glow, spotColor = glow)
            .clip(outer)
            .background(fill)
            .border(if (neon) 1.6.dp else 1.2.dp, rim, outer)
            .drawWithContent {
                drawContent()
                if (glassy) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0x47FFFFFF), Color.Transparent),
                            endY = this.size.height * 0.5f
                        ),
                        size = Size(this.size.width, this.size.height * 0.5f)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(size * 0.74f).clip(inner),
            contentScale = ContentScale.Crop,
            colorFilter = if (style == "mono") ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
        )
    }
}

@Composable
fun StatCard(label: String, value: String, progress: Float, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(modifier.glass(RoundedCornerShape(22.dp)).padding(14.dp)) {
        Text(label.uppercase(), color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(accent, Nebula)))
            )
        }
    }
}

@Composable
fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .glass(shape)
            .then(if (selected) Modifier.border(1.5.dp, accent, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (selected) accent else TextPrimary, fontSize = 14.sp)
    }
}

/** Horizontal swipe detector for side pages. */
fun Modifier.horizontalSwipe(
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null
): Modifier = pointerInput(Unit) {
    var dx = 0f
    var dy = 0f
    detectDragGestures(
        onDragStart = { dx = 0f; dy = 0f },
        onDrag = { _, d -> dx += d.x; dy += d.y },
        onDragEnd = {
            if (abs(dx) > abs(dy) * 1.5f) {
                if (dx < -150f) onSwipeLeft?.invoke() else if (dx > 150f) onSwipeRight?.invoke()
            }
        }
    )
}
