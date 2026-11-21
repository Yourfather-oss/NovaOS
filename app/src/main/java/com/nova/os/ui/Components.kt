package com.nova.os.ui

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

@Composable
fun NovaIcon(app: AppInfo, size: Dp, shape: String, modifier: Modifier = Modifier) {
    val s = when (shape) {
        "circle" -> CircleShape
        "original" -> RoundedCornerShape(0.dp)
        else -> RoundedCornerShape(size * 0.3f)
    }
    val m = if (shape == "original") {
        modifier.size(size)
    } else {
        modifier
            .size(size)
            .shadow(8.dp, s, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(s)
            .border(1.dp, Brush.linearGradient(listOf(Color(0x77FFFFFF), Color(0x11FFFFFF))), s)
    }
    Image(bitmap = app.icon, contentDescription = app.label, modifier = m, contentScale = ContentScale.Crop)
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
