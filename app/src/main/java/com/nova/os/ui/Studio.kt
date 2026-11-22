package com.nova.os.ui

import kotlin.math.roundToInt
import java.io.File
import com.nova.os.data.importWallpaper
import com.nova.os.data.WALLPAPER_FILE
import com.nova.os.data.ThemePresets
import com.nova.os.data.ThemePreset
import com.nova.os.data.AppInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.data.NovaPrefs
import com.nova.os.data.SettingsStore
import kotlinx.coroutines.launch

@Composable
fun DesignStudioScreen(
    prefs: NovaPrefs,
    store: SettingsStore,
    apps: List<AppInfo>,
    onEditPages: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current

    Column(
        pageModifier()
            .blockTouches()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("DESIGN STUDIO", color = accent, fontSize = 12.sp, letterSpacing = 4.sp)
        Spacer(Modifier.height(6.dp))
        Text("Changes apply instantly", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))

        var sizeDraft by remember(prefs.iconSize) { mutableFloatStateOf(prefs.iconSize.toFloat()) }
        var dimDraft by remember(prefs.wallpaperDim) { mutableFloatStateOf(prefs.wallpaperDim.toFloat()) }
        val hasPhoto = remember(prefs.wallpaperRev, prefs.wallpaper) { File(context.filesDir, WALLPAPER_FILE).exists() }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    if (importWallpaper(context, uri)) {
                        store.put("wallpaper_rev", System.currentTimeMillis().toString())
                        store.setWallpaper("custom")
                    } else {
                        Toast.makeText(context, "Couldn't use that photo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Preview(prefs, apps, sizeDraft.roundToInt())

        Spacer(Modifier.height(24.dp))
        Label("THEMES")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemePresets.forEach { t ->
                val active = prefs.accent == t.accent && prefs.wallpaper == t.wallpaper &&
                    prefs.surface == t.surface && prefs.iconShape == t.iconShape &&
                    prefs.iconFinish == t.iconFinish && prefs.clockFont == t.clockFont &&
                    prefs.clockWeight == t.clockWeight
                ThemeCard(t, active) { scope.launch { store.applyTheme(t) } }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Tap a theme to set wallpaper, colours, icons and clock together. You can fine-tune below.", color = TextMuted, fontSize = 12.sp)

        Spacer(Modifier.height(28.dp))
        Label("HOME MODE")
        ChipRow(
            listOf("nova" to "NOVA", "classic" to "Classic"),
            prefs.homeMode
        ) { scope.launch { store.put("home_mode", it) } }
        Spacer(Modifier.height(8.dp))
        Text(
            if (prefs.homeMode == "classic") "All apps sit on home pages. Swipe up for the A–Z drawer."
            else "Clean home with the A–Z app drawer. Swipe up to open it.",
            color = TextMuted, fontSize = 12.sp
        )

        if (prefs.homeMode == "classic") {
            Spacer(Modifier.height(20.dp))
            Label("PAGE GRID  (columns × rows)")
            ChipRow(
                listOf("4x5" to "4×5", "4x6" to "4×6", "5x6" to "5×6"),
                prefs.gridPreset
            ) { scope.launch { store.put("grid_preset", it) } }
            Spacer(Modifier.height(12.dp))
            StudioButton("Edit home pages") { onEditPages() }
        }

        Spacer(Modifier.height(28.dp))
        Label("HOME WIDGETS")
        ToggleRow("Weather", prefs.homeWeather) { scope.launch { store.put("w_weather", it) } }
        ToggleRow("System rings", prefs.homeStats) { scope.launch { store.put("w_stats", it) } }
        ToggleRow("Upcoming today", prefs.homeUpcoming) { scope.launch { store.put("w_upcoming", it) } }
        ToggleRow("Music player", prefs.homeMusic) { scope.launch { store.put("w_music", it) } }
        ToggleRow("Frequently used apps", prefs.homeFrequent) { scope.launch { store.put("w_frequent", it) } }
        ToggleRow("Note card", prefs.homeNote) { scope.launch { store.put("w_note", it) } }
        Spacer(Modifier.height(4.dp))
        Spacer(Modifier.height(20.dp))
        Label("HOME ORDER (TOP TO BOTTOM)")
        val homeOrder = normalizeHomeOrder(prefs.homeOrder)
        homeOrder.forEachIndexed { i, id ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(homeSectionLabel(id), color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                OrderButton("▲", i > 0) {
                    val l = homeOrder.toMutableList()
                    l[i] = l[i - 1]; l[i - 1] = id
                    scope.launch { store.put("home_order", l.joinToString(",")) }
                }
                Spacer(Modifier.width(8.dp))
                OrderButton("▼", i < homeOrder.lastIndex) {
                    val l = homeOrder.toMutableList()
                    l[i] = l[i + 1]; l[i + 1] = id
                    scope.launch { store.put("home_order", l.joinToString(",")) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Tip: long-press the Frequently used row on the home screen to pin, unpin or move it.", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text("If the screen is too short for everything, NOVA hides widgets from the bottom of this order automatically.", color = TextMuted, fontSize = 12.sp)

        Spacer(Modifier.height(28.dp))
        Label("DOCK STYLE")
        ChipRow(
            listOf("floating" to "Floating", "flat" to "Flat", "none" to "No background"),
            prefs.dockStyle
        ) { scope.launch { store.put("dock_style", it) } }

        Spacer(Modifier.height(28.dp))
        Label("SURFACE")
        ChipRow(
            listOf("glass" to "Glass", "matte" to "Matte", "clear" to "Clear"),
            prefs.surface
        ) { scope.launch { store.setSurface(it) } }

        Spacer(Modifier.height(24.dp))
        Label("ACCENT")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            AccentChoices.forEach { (name, color) ->
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (prefs.accent == name) 3.dp else 0.dp, TextPrimary, CircleShape)
                        .clickable { scope.launch { store.setAccent(name) } }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Label("WALLPAPER")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WallpaperNames.forEach { (id, label) ->
                WallpaperTile(label, wallpaperSwatch(id), prefs.wallpaper == id) { scope.launch { store.setWallpaper(id) } }
            }
            if (hasPhoto) {
                WallpaperTile("Your photo", listOf(Color(0xFF4A4F5C), Color(0xFF1A1D24)), prefs.wallpaper == "custom") {
                    scope.launch { store.setWallpaper("custom") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StudioButton("Choose a photo from your gallery") {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (prefs.wallpaper == "custom") {
            Spacer(Modifier.height(10.dp))
            Text("Dim photo  ${dimDraft.roundToInt()}%", color = TextPrimary, fontSize = 14.sp)
            Slider(
                value = dimDraft,
                onValueChange = { dimDraft = it },
                onValueChangeFinished = { scope.launch { store.setWallpaperDim(dimDraft.roundToInt()) } },
                valueRange = 0f..80f,
                colors = SliderDefaults.colors(
                    thumbColor = LocalAccent.current,
                    activeTrackColor = LocalAccent.current,
                    inactiveTrackColor = GlassFill
                )
            )
        }
        Spacer(Modifier.height(8.dp))
        ToggleRow("Animated wallpaper", prefs.liveWallpaper) { scope.launch { store.setLiveWallpaper(it) } }

        Spacer(Modifier.height(24.dp))
        Label("ICON SHAPE")
        ChipRow(
            listOf(
                "squircle" to "Squircle", "circle" to "Circle", "rounded" to "Rounded",
                "teardrop" to "Teardrop", "hexagon" to "Hexagon", "original" to "Original"
            ),
            prefs.iconShape
        ) { scope.launch { store.setIconShape(it) } }

        Spacer(Modifier.height(24.dp))
        Label("ICON STYLE")
        ChipRow(
            listOf(
                "glass" to "Glass", "tile" to "Dark tile", "neon" to "Neon",
                "mono" to "Mono glass", "plain" to "Plain"
            ),
            prefs.iconFinish
        ) { scope.launch { store.put("icon_finish", it) } }

        Spacer(Modifier.height(24.dp))
        Label("ICON SIZE  ${sizeDraft.roundToInt()} dp")
        Slider(
            value = sizeDraft,
            onValueChange = { sizeDraft = it },
            onValueChangeFinished = { scope.launch { store.setIconSize(sizeDraft.roundToInt()) } },
            valueRange = 40f..76f,
            steps = 17,
            colors = SliderDefaults.colors(
                thumbColor = LocalAccent.current,
                activeTrackColor = LocalAccent.current,
                inactiveTrackColor = GlassFill
            )
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow("Show app labels", prefs.showLabels) { scope.launch { store.setShowLabels(it) } }

        Spacer(Modifier.height(24.dp))
        Label("CLOCK FONT")
        ChipRow(
            listOf("default" to "Default", "serif" to "Serif", "mono" to "Mono"),
            prefs.clockFont
        ) { scope.launch { store.setClockFont(it) } }

        Spacer(Modifier.height(24.dp))
        Label("CLOCK WEIGHT")
        ChipRow(
            listOf("thin" to "Thin", "light" to "Light", "regular" to "Regular"),
            prefs.clockWeight
        ) { scope.launch { store.setClockWeight(it) } }

        Spacer(Modifier.height(24.dp))
        Label("CLOCK SIZE")
        ChipRow(
            listOf("s" to "Small", "m" to "Medium", "l" to "Large"),
            prefs.clockSize
        ) { scope.launch { store.setClockSize(it) } }
        Spacer(Modifier.height(8.dp))
        ToggleRow("24-hour clock", prefs.use24h) { scope.launch { store.setUse24h(it) } }

        Spacer(Modifier.height(24.dp))
        Label("MOTION")
        ChipRow(
            listOf("fast" to "Fast", "normal" to "Normal", "slow" to "Slow", "off" to "Off"),
            prefs.animSpeed
        ) { scope.launch { store.setAnimSpeed(it) } }

        Spacer(Modifier.height(24.dp))
        Label("GESTURES  (tap a row to change)")
        val gestures = listOf(
            Triple("Swipe up", "g_up", prefs.gestureUp),
            Triple("Swipe down", "g_down", prefs.gestureDown),
            Triple("Swipe left", "g_left", prefs.gestureLeft),
            Triple("Swipe right", "g_right", prefs.gestureRight),
            Triple("Long press", "g_long", prefs.gestureLong),
            Triple("Double tap", "g_double", prefs.gestureDouble)
        )
        gestures.forEach { (label, key, value) ->
            GestureRow(label, gestureName(value)) {
                val others = gestures.filter { it.second != key }.map { it.third }
                scope.launch { store.put(key, nextGesture(value, others)) }
            }
        }

        Spacer(Modifier.height(24.dp))
        Label("PRIVACY")
        Text(
            if (prefs.hidden.isEmpty()) "No hidden apps. Long-press an app in the drawer and choose Hide app."
            else "${prefs.hidden.size} hidden app(s)",
            color = TextMuted, fontSize = 13.sp
        )
        if (prefs.hidden.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            StudioButton("Unhide all apps") { scope.launch { store.put("hidden", "") } }
        }

        Spacer(Modifier.height(32.dp))
        StudioButton("Set NOVA as default home app") {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Spacer(Modifier.height(12.dp))
        StudioButton("Back", onClick = onBack)
    }
}

@Composable
private fun Preview(prefs: NovaPrefs, apps: List<AppInfo>, iconSize: Int) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(18.dp)) {
        Text("PREVIEW", color = TextMuted, fontSize = 10.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))
        BasicText(
            "10:24",
            style = TextStyle(
                brush = Brush.horizontalGradient(listOf(TextPrimary, TextPrimary, accent)),
                fontSize = (clockSizeSp(prefs.clockSize) * 0.65f).sp,
                fontWeight = clockWeight(prefs.clockWeight),
                fontFamily = clockFamily(prefs.clockFont)
            )
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.take(4).forEach { NovaIcon(it, iconSize.dp, prefs.iconShape) }
        }
    }
}

@Composable
private fun ThemeCard(preset: ThemePreset, active: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val accentColor = accentFor(preset.accent)
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .width(112.dp)
            .height(150.dp)
            .clip(shape)
            .background(Brush.linearGradient(wallpaperSwatch(preset.wallpaper)))
            .border(if (active) 2.5.dp else 1.dp, if (active) accent else GlassBorder, shape)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(iconShapeFor(preset.iconShape, 18.dp))
                            .background(Color.White.copy(alpha = 0.55f))
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(preset.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WallpaperTile(label: String, colors: List<Color>, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(18.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(72.dp)
                .height(104.dp)
                .clip(shape)
                .background(Brush.linearGradient(colors))
                .border(if (selected) 2.5.dp else 1.dp, if (selected) accent else GlassBorder, shape)
                .clickable(onClick = onClick)
        )
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (selected) accent else TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun OrderButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GlassFill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) TextPrimary else TextMuted, fontSize = 14.sp)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = TextMuted, fontSize = 11.sp, letterSpacing = 3.sp)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { (value, label) ->
            OptionChip(label, selected == value) { onSelect(value) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accent)
        )
    }
}

@Composable
private fun StudioButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .glass(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Normal) }
}

private val gestureOptions = listOf("drawer", "control", "dashboard", "widgets", "studio", "mode", "none")

private fun gestureName(k: String) = when (k) {
    "drawer" -> "App drawer"
    "control" -> "Control centre"
    "dashboard" -> "Dashboard"
    "widgets" -> "Widgets"
    "studio" -> "Design Studio"
    "mode" -> "Toggle home mode"
    else -> "Nothing"
}

/** Next action in the cycle; never leaves the user without a way to reach the Studio. */
private fun nextGesture(current: String, others: List<String>): String {
    var i = gestureOptions.indexOf(current)
    repeat(gestureOptions.size) {
        i = (i + 1) % gestureOptions.size
        val candidate = gestureOptions[i]
        if (candidate == "studio" || others.contains("studio")) return candidate
    }
    return current
}

@Composable
private fun GestureRow(label: String, value: String, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .glass(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = accent, fontSize = 14.sp)
    }
}
