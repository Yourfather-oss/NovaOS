package com.nova.os.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.data.AppInfo
import com.nova.os.data.NovaPrefs
import com.nova.os.data.SettingsStore
import com.nova.os.data.SysStats
import com.nova.os.data.defaultDock
import com.nova.os.data.launchApp
import com.nova.os.data.loadApps
import com.nova.os.data.openAppInfo
import com.nova.os.data.readStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private enum class Screen { Splash, Home, Drawer, Settings }

@Composable
fun NovaApp(homeSignal: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore(context) }
    val prefs by store.flow.collectAsState(initial = NovaPrefs())

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { apps = withContext(Dispatchers.Default) { loadApps(context) } }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { refresh++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    var screen by remember { mutableStateOf(Screen.Splash) }
    LaunchedEffect(Unit) { delay(1600); screen = Screen.Home }
    LaunchedEffect(homeSignal) { if (screen != Screen.Splash) screen = Screen.Home }
    BackHandler(enabled = screen == Screen.Drawer || screen == Screen.Settings) { screen = Screen.Home }

    val dockPackages = prefs.dock ?: defaultDock(apps)
    val dockApps = dockPackages.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }

    fun toggleDock(app: AppInfo) {
        val current = dockPackages.toMutableList()
        if (!current.remove(app.packageName) && current.size < 5) current.add(app.packageName)
        scope.launch { store.setDock(current) }
    }

    val overlayOpen = screen == Screen.Drawer || screen == Screen.Settings
    val homeScale by animateFloatAsState(if (overlayOpen) 0.92f else 1f, tween(380), label = "homeScale")
    val homeAlpha by animateFloatAsState(if (overlayOpen) 0.3f else 1f, tween(380), label = "homeAlpha")

    val enter = slideInVertically(tween(380)) { it / 4 } + fadeIn(tween(380))
    val exit = slideOutVertically(tween(300)) { it / 4 } + fadeOut(tween(250))

    NovaTheme(prefs.accent) {
        Box(Modifier.fillMaxSize().background(Obsidian)) {
            NovaBackdrop(prefs.wallpaper)

            if (screen != Screen.Splash) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = homeScale; scaleY = homeScale; alpha = homeAlpha }
                ) {
                    HomeScreen(
                        prefs = prefs,
                        dock = dockApps,
                        onOpenDrawer = { screen = Screen.Drawer },
                        onOpenSettings = { screen = Screen.Settings }
                    )
                }
            }

            AnimatedVisibility(
                visible = screen == Screen.Splash,
                enter = fadeIn(),
                exit = fadeOut(tween(500))
            ) { SplashScreen() }

            AnimatedVisibility(visible = screen == Screen.Drawer, enter = enter, exit = exit) {
                DrawerScreen(
                    apps = apps,
                    dockPackages = dockPackages,
                    iconShape = prefs.iconShape,
                    onToggleDock = ::toggleDock,
                    onClose = { screen = Screen.Home }
                )
            }

            AnimatedVisibility(visible = screen == Screen.Settings, enter = enter, exit = exit) {
                SettingsScreen(
                    prefs = prefs,
                    onAccent = { scope.launch { store.setAccent(it) } },
                    onUse24h = { scope.launch { store.setUse24h(it) } },
                    onWallpaper = { scope.launch { store.setWallpaper(it) } },
                    onIconShape = { scope.launch { store.setIconShape(it) } },
                    onBack = { screen = Screen.Home }
                )
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(900), label = "splash")
    Column(
        Modifier.fillMaxSize().alpha(alpha),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("NOVA", color = TextPrimary, fontSize = 52.sp, fontWeight = FontWeight.Light, letterSpacing = 14.sp)
        Spacer(Modifier.height(10.dp))
        Text("OPERATING EXPERIENCE", color = LocalAccent.current, fontSize = 11.sp, letterSpacing = 5.sp)
        Spacer(Modifier.height(4.dp))
        Text("BEYOND THE ORDINARY", color = TextMuted, fontSize = 10.sp, letterSpacing = 4.sp)
    }
}

@Composable
private fun HomeScreen(
    prefs: NovaPrefs,
    dock: List<AppInfo>,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccent.current

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000 - (System.currentTimeMillis() % 1000))
        }
    }
    var stats by remember { mutableStateOf(SysStats()) }
    LaunchedEffect(Unit) {
        while (true) {
            stats = withContext(Dispatchers.IO) { readStats(context) }
            delay(15000)
        }
    }

    val time = now.format(DateTimeFormatter.ofPattern(if (prefs.use24h) "HH:mm" else "hh:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
    val greeting = when {
        now.hour < 12 -> "GOOD MORNING"
        now.hour < 17 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dy = 0f
                var dx = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDrag = { _, d -> dx += d.x; dy += d.y },
                    onDragEnd = { if (abs(dy) > abs(dx) && dy < -120f) onOpenDrawer() }
                )
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onOpenSettings() }) }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(Modifier.align(Alignment.TopStart).padding(top = 56.dp)) {
            Text(greeting, color = accent, fontSize = 12.sp, letterSpacing = 4.sp)
            Spacer(Modifier.height(4.dp))
            BasicText(
                time,
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(TextPrimary, TextPrimary, accent)),
                    fontSize = 92.sp,
                    fontWeight = FontWeight.Thin
                )
            )
            Text(date, color = TextMuted, fontSize = 16.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    if (stats.charging) "Charging" else "Battery",
                    "${stats.battery}%", stats.battery / 100f, Modifier.weight(1f)
                )
                StatCard("Storage", "${stats.storagePct}%", stats.storagePct / 100f, Modifier.weight(1f))
                StatCard("Memory", "${stats.ramPct}%", stats.ramPct / 100f, Modifier.weight(1f))
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .glass(RoundedCornerShape(26.dp))
                    .clickable { onOpenDrawer() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) { Text("Search apps", color = TextMuted, fontSize = 15.sp) }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(30.dp))
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dock.isEmpty()) {
                    Text("Long-press an app in the drawer to pin it here", color = TextMuted, fontSize = 12.sp)
                }
                dock.forEach { app ->
                    NovaIcon(
                        app, 54.dp, prefs.iconShape,
                        Modifier.bounceClick { launchApp(context, app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerScreen(
    apps: List<AppInfo>,
    dockPackages: List<String>,
    iconShape: String,
    onToggleDock: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { app ->
            app.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
        }.toSortedMap()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Obsidian.copy(alpha = 0.90f), Obsidian.copy(alpha = 0.97f))))
            .blockTouches()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(GlassBorder)
                .clickable { onClose() }
        )
        Spacer(Modifier.height(16.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .glass(RoundedCornerShape(26.dp)),
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search apps", color = TextMuted, fontSize = 16.sp)
                    inner()
                }
            }
        )
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize()) {
            grouped.forEach { (letter, list) ->
                if (query.isBlank()) {
                    item(key = "header_$letter", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            letter.toString(), color = accent, fontSize = 13.sp, letterSpacing = 3.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp)
                        )
                    }
                }
                items(list, key = { it.component.flattenToString() }) { app ->
                    var menu by remember { mutableStateOf(false) }
                    val pinned = app.packageName in dockPackages
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 2.dp)
                                .bounceClick(onLongClick = { menu = true }) { launchApp(context, app) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NovaIcon(app, 54.dp, iconShape)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                app.label, color = TextPrimary, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (pinned) "Remove from dock" else "Add to dock") },
                                onClick = { menu = false; onToggleDock(app) }
                            )
                            DropdownMenuItem(
                                text = { Text("App info") },
                                onClick = { menu = false; openAppInfo(context, app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    prefs: NovaPrefs,
    onAccent: (String) -> Unit,
    onUse24h: (Boolean) -> Unit,
    onWallpaper: (String) -> Unit,
    onIconShape: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccent.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Obsidian.copy(alpha = 0.90f), Obsidian.copy(alpha = 0.97f))))
            .blockTouches()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("NOVA Settings", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(28.dp))

        SectionLabel("WALLPAPER")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OptionChip("Aurora", prefs.wallpaper == "aurora") { onWallpaper("aurora") }
            OptionChip("Nebula", prefs.wallpaper == "nebula") { onWallpaper("nebula") }
            OptionChip("Midnight", prefs.wallpaper == "midnight") { onWallpaper("midnight") }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("ICON SHAPE")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OptionChip("Squircle", prefs.iconShape == "squircle") { onIconShape("squircle") }
            OptionChip("Circle", prefs.iconShape == "circle") { onIconShape("circle") }
            OptionChip("Original", prefs.iconShape == "original") { onIconShape("original") }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("ACCENT")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("cyan" to Cyan, "violet" to Nebula).forEach { (name, color) ->
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (prefs.accent == name) 3.dp else 0.dp, TextPrimary, CircleShape)
                        .clickable { onAccent(name) }
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("24-hour clock", color = TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = prefs.use24h,
                onCheckedChange = onUse24h,
                colors = SwitchDefaults.colors(checkedTrackColor = accent)
            )
        }

        Spacer(Modifier.height(28.dp))
        SettingsButton("Set NOVA as default home app") {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsButton("Back") { onBack() }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 11.sp, letterSpacing = 3.sp)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .glass(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = TextPrimary, fontSize = 15.sp) }
}
