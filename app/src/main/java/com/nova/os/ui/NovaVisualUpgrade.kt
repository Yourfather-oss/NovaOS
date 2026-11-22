package com.nova.os.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NovaInk = Color(0xFF090D14)
private val NovaPanel = Color(0xFF151C28)
private val NovaPanelLight = Color(0xFF202B3A)
private val NovaWhite = Color(0xFFF4F7FC)
private val NovaMuted = Color(0xFF8D9AAF)
private val NovaCyan = Color(0xFF55DDF2)
private val NovaViolet = Color(0xFF9B8CFF)

/**
 * Compact activity capsule for the home screen.
 * Supply real playback/timer/charging state from the host launcher.
 */
@Composable
fun NovaLiveCapsule(
    title: String,
    subtitle: String,
    symbol: String = "♫",
    active: Boolean = true,
    expanded: Boolean = false,
    accent: Color = NovaCyan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (expanded) 1.015f else 1f,
        animationSpec = spring(stiffness = 550f),
        label = "capsuleScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF1A2635), Color(0xFF111722), Color(0xFF211D35))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(symbol, color = accent, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = NovaWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = NovaMuted, fontSize = 11.sp, maxLines = 1)
            }
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(if (active) accent else NovaMuted.copy(alpha = .45f))
            )
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) "⌃" else "⌄", color = NovaMuted, fontSize = 18.sp)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = .10f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(.38f).height(3.dp).clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(accent, NovaViolet)))
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CapsuleAction("Previous", "‹") {}
                    CapsuleAction("Pause", "Ⅱ") {}
                    CapsuleAction("Next", "›") {}
                }
            }
        }
    }
}

@Composable
private fun CapsuleAction(label: String, glyph: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, color = NovaWhite, fontSize = 20.sp)
        Text(label, color = NovaMuted, fontSize = 10.sp)
    }
}

data class NovaCommand(
    val title: String,
    val description: String,
    val symbol: String,
    val onRun: () -> Unit
)

/**
 * Search-first command palette. The caller supplies installed apps, contacts,
 * settings and shortcuts as NovaCommand entries.
 */
@Composable
fun NovaCommandPalette(
    visible: Boolean,
    commands: List<NovaCommand>,
    onDismiss: () -> Unit,
    accent: Color = NovaCyan
) {
    if (!visible) return
    var query by remember(visible) { mutableStateOf("") }
    val filtered = remember(query, commands) {
        if (query.isBlank()) commands
        else commands.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }

    Box(
        Modifier.fillMaxWidth().background(Color(0xCC05080E)).padding(horizontal = 16.dp, vertical = 28.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF202B3B), Color(0xFF111722))))
                .border(1.dp, Color.White.copy(alpha = .13f), RoundedCornerShape(30.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NOVA COMMAND", color = accent, fontSize = 10.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("What do you need?", color = NovaWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("✕", color = NovaMuted, fontSize = 19.sp,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(10.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                    .background(Color.Black.copy(alpha = .22f))
                    .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(17.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⌕", color = accent, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = NovaWhite, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("Search apps, contacts, actions…", color = NovaMuted, fontSize = 13.sp)
                            inner()
                        }
                    }
                )
                if (query.isNotEmpty()) {
                    Text("CLEAR", color = accent, fontSize = 9.sp,
                        modifier = Modifier.clickable { query = "" }.padding(4.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(if (query.isBlank()) "QUICK ACTIONS" else "${filtered.size} RESULTS",
                color = NovaMuted, fontSize = 9.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height( minOf(360.dp, (filtered.size.coerceAtLeast(1) * 66).dp) ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.title }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                            .clickable { item.onRun(); onDismiss() }
                            .background(Color.White.copy(alpha = .045f))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(13.dp))
                                .background(accent.copy(alpha = .13f)),
                            contentAlignment = Alignment.Center
                        ) { Text(item.symbol, color = accent, fontSize = 17.sp) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = NovaWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(item.description, color = NovaMuted, fontSize = 10.sp, maxLines = 1)
                        }
                        Text("›", color = NovaMuted, fontSize = 20.sp)
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text("No matching commands", color = NovaMuted, fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("ESC  ·  CLOSE", color = NovaMuted, fontSize = 9.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onDismiss).padding(8.dp))
        }
    }
}

/** Premium tile used by a redesigned quick settings/control centre. */
@Composable
fun NovaControlTile(
    title: String,
    value: String,
    symbol: String,
    enabled: Boolean,
    accent: Color = NovaCyan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileColor = if (enabled) accent.copy(alpha = .15f) else NovaPanel.copy(alpha = .88f)
    Column(
        modifier = modifier.clip(RoundedCornerShape(22.dp))
            .background(tileColor)
            .border(1.dp, if (enabled) accent.copy(alpha = .36f) else Color.White.copy(alpha = .07f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, color = if (enabled) accent else NovaMuted, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (enabled) accent else NovaMuted.copy(alpha = .35f)))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = NovaWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = NovaMuted, fontSize = 10.sp)
        }
    }
}

/** A visually consistent section heading for home widgets and settings pages. */
@Composable
fun NovaSectionHeading(
    title: String,
    subtitle: String? = null,
    action: String? = null,
    accent: Color = NovaCyan,
    onAction: (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), color = accent, fontSize = 10.sp, letterSpacing = 2.2.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = NovaMuted, fontSize = 11.sp)
            }
        }
        if (action != null) {
            Text(action, color = NovaWhite, fontSize = 11.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = onAction != null) { onAction?.invoke() }
                    .background(Color.White.copy(alpha = .07f))
                    .padding(horizontal = 11.dp, vertical = 8.dp))
        }
    }
}
