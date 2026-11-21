package com.nova.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val Obsidian = Color(0xFF080B12)
val DeepSpace = Color(0xFF101B2A)
val Cyan = Color(0xFF13B8D8)
val Nebula = Color(0xFF7567FF)
val GlassFill = Color(0x22FFFFFF)
val GlassBorder = Color(0x33FFFFFF)
val TextPrimary = Color(0xFFF2F6FA)
val TextMuted = Color(0xFF9AA8B8)

val LocalAccent = staticCompositionLocalOf { Cyan }

fun accentFor(name: String): Color = if (name == "violet") Nebula else Cyan

@Composable
fun NovaTheme(accentName: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccent provides accentFor(accentName), content = content)
}

/** Simulated frosted glass: light gradient fill with a bright top edge. */
fun Modifier.glass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x0FFFFFFF))))
    .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))), shape)
