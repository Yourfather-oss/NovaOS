package com.nova.os.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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

    // Re-scan when apps are installed or removed.
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

    NovaTheme(prefs.accent) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DeepSpace, Obsidian)))
        ) {
            Crossfade(targetState = screen, label = "screen") { s ->
                when (s) {
                    Screen.Splash -> SplashScreen()
                    Screen.Home -> HomeScreen(
                        prefs = prefs,
                        dock = dockApps,
                        onOpenDrawer = { screen = Screen.Drawer },
                        onOpenSettings = { screen = Screen.Settings }
                    )
                    Screen.Drawer -> DrawerScreen(
                        apps = apps,
                        dockPackages = dockPackages,
                        onToggleDock = ::toggleDock,
                        onClose = { screen = Screen.Home }
                    )
                    Screen.Settings -> SettingsScreen(
                        prefs = prefs,
                        onAccent = { scope.launch { store.setAccent(it) } },
                        onUse24h = { scope.launch { store.setUse24h(it) } },
                        onBack = { screen = Screen.Home }
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (visible) 1f else 0f,
        androidx.compose.animation.core.tween(900),
        label = "splash"
    )
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
    val time = now.format(DateTimeFormatter.ofPattern(if (prefs.use24h) "HH:mm" else "hh:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dy = 0f; var dx = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDrag = { _, d -> dx += d.x; dy += d.y },
                    onDragEnd = {
                        if (abs(dy) > abs(dx) && dy < -120f) onOpenDrawer()
                        // Swipe down / left / right reserved for control centre,
                        // intelligence dashboard and widget space (later milestones).
                    }
                )
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onOpenSettings() }) }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(Modifier.align(Alignment.TopStart).padding(top = 72.dp)) {
            Text(time, color = TextPrimary, fontSize = 84.sp, fontWeight = FontWeight.Thin)
            Text(date, color = TextMuted, fontSize = 16.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(36.dp).height(2.dp).background(accent))
        }

        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(GlassFill)
                    .border(1.dp, GlassBorder, RoundedCornerShape(26.dp))
                    .clickable { onOpenDrawer() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) { Text("Search apps", color = TextMuted, fontSize = 15.sp) }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(GlassFill)
                    .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (dock.isEmpty()) {
                    Text("Long-press an app in the drawer to pin it here", color = TextMuted, fontSize = 12.sp)
                }
                dock.forEach { app ->
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(52.dp).clickable { launchApp(context, app) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerScreen(
    apps: List<AppInfo>,
    dockPackages: List<String>,
    onToggleDock: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Obsidian.copy(alpha = 0.92f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp).height(4.dp)
                .clip(CircleShape).background(GlassBorder)
                .clickable { onClose() }
        )
        Spacer(Modifier.height(16.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(GlassFill)
                .border(1.dp, GlassBorder, RoundedCornerShape(26.dp)),
            decorationBox = { inner ->
                Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search apps", color = TextMuted, fontSize = 16.sp)
                    inner()
                }
            }
        )
        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.component.flattenToString() }) { app ->
                var menu by remember { mutableStateOf(false) }
                val pinned = app.packageName in dockPackages
                Box {
                    Column(
                        Modifier
                            .padding(vertical = 10.dp)
                            .combinedClickable(
                                onClick = { launchApp(context, app) },
                                onLongClick = { menu = true }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(54.dp))
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

@Composable
private fun SettingsScreen(
    prefs: NovaPrefs,
    onAccent: (String) -> Unit,
    onUse24h: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccent.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Text("NOVA Settings", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(28.dp))

        Text("ACCENT", color = TextMuted, fontSize = 11.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(12.dp))
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
private fun SettingsButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(GlassFill)
            .border(1.dp, GlassBorder, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = TextPrimary, fontSize = 15.sp) }
}
