package com.nova.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val Obsidian = Color(0xFF080B12)
val DeepSpace = Color(0xFF101B2A)
val Cyan = Color(0xFF13B8D8)
val Nebula = Color(0xFF7567FF)
val GlassFill = Color(0x22FFFFFF)
val GlassBorder = Color(0x33FFFFFF)
val TextPrimary = Color(0xFFF2F6FA)
val TextMuted = Color(0xFF9AA8B8)

val AccentChoices: List<Pair<String, Color>> = listOf(
    "cyan" to Cyan,
    "violet" to Nebula,
    "mint" to Color(0xFF2DD4A7),
    "amber" to Color(0xFFFFB020),
    "rose" to Color(0xFFFF5C8A),
    "blue" to Color(0xFF4C8DFF)
)

val LocalAccent = staticCompositionLocalOf { Cyan }
val LocalSurface = staticCompositionLocalOf { "glass" }

fun accentFor(name: String): Color = AccentChoices.firstOrNull { it.first == name }?.second ?: Cyan

fun clockWeight(name: String): FontWeight = when (name) {
    "regular" -> FontWeight.Normal
    "light" -> FontWeight.Light
    else -> FontWeight.Thin
}

fun clockFamily(name: String): FontFamily = when (name) {
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    else -> FontFamily.Default
}

fun clockSizeSp(name: String): Int = when (name) {
    "s" -> 72
    "l" -> 112
    else -> 92
}

@Composable
fun NovaTheme(accentName: String, surface: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAccent provides accentFor(accentName),
        LocalSurface provides surface,
        content = content
    )
}

/** Surface style used by every card, bar and tile. Follows the Design Studio setting. */
fun Modifier.glass(shape: Shape): Modifier = composed {
    when (LocalSurface.current) {
        "matte" -> this
            .clip(shape)
            .background(Color(0xFF161F2D))
            .border(1.dp, Color(0x22FFFFFF), shape)
        "clear" -> this
            .clip(shape)
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x22FFFFFF))), shape)
        else -> this
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x0FFFFFFF))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))), shape)
    }
}
