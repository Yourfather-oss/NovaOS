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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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

private enum class Screen { Splash, Home, Drawer, Settings, Control, Dashboard, Widgets }

@Composable
fun NovaApp(homeSignal: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore(context) }
    val prefs by store.flow.collectAsState(initial = NovaPrefs())
    val speed = when (prefs.animSpeed) {
        "fast" -> 0.6f
        "slow" -> 1.6f
        "off" -> 0f
        else -> 1f
    }
    fun ms(v: Int): Int = (v * speed).toInt()

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
    BackHandler(enabled = screen != Screen.Splash && screen != Screen.Home) { screen = Screen.Home }

    val visibleApps = remember(apps, prefs.hidden) { apps.filter { it.packageName !in prefs.hidden } }
    val dockPackages = prefs.dock ?: defaultDock(visibleApps)
    val dockApps = dockPackages.mapNotNull { pkg -> visibleApps.firstOrNull { it.packageName == pkg } }

    fun toggleDock(app: AppInfo) {
        val current = dockPackages.toMutableList()
        if (!current.remove(app.packageName) && current.size < 5) current.add(app.packageName)
        scope.launch { store.setDock(current) }
    }

    fun launchTracked(app: AppInfo) {
        launchApp(context, app)
        scope.launch { store.logLaunch(app.packageName) }
    }

    fun runAction(action: String) {
        when (action) {
            "drawer" -> screen = Screen.Drawer
            "control" -> screen = Screen.Control
            "dashboard" -> screen = Screen.Dashboard
            "widgets" -> screen = Screen.Widgets
            "studio" -> screen = Screen.Settings
            "mode" -> scope.launch {
                store.put("home_mode", if (prefs.homeMode == "classic") "nova" else "classic")
            }
            else -> {}
        }
    }

    fun hideApp(app: AppInfo) {
        val next = (prefs.hidden + app.packageName).distinct().joinToString(",")
        scope.launch { store.put("hidden", next) }
    }

    val recent = remember(prefs.launchLog, visibleApps) { recentApps(prefs.launchLog, visibleApps, 5) }

    var timerPreset by remember { mutableIntStateOf(25) }
    var timerLeft by remember { mutableIntStateOf(25 * 60) }
    var timerRunning by remember { mutableStateOf(false) }
    LaunchedEffect(timerRunning) {
        while (timerRunning && timerLeft > 0) {
            delay(1000)
            timerLeft--
        }
        if (timerLeft <= 0) timerRunning = false
    }
    val timerText = if (timerRunning) "%02d:%02d".format(timerLeft / 60, timerLeft % 60) else null

    val overlayOpen = screen != Screen.Splash && screen != Screen.Home
    val homeScale by animateFloatAsState(if (overlayOpen) 0.92f else 1f, tween(ms(380)), label = "homeScale")
    val homeAlphaTarget = when (screen) {
        Screen.Drawer, Screen.Settings, Screen.Dashboard, Screen.Widgets -> 0f
        Screen.Control -> 0.5f
        else -> 1f
    }
    val homeAlpha by animateFloatAsState(homeAlphaTarget, tween(ms(380)), label = "homeAlpha")

    val enter = slideInVertically(tween(ms(380))) { it / 4 } + fadeIn(tween(ms(380)))
    val exit = slideOutVertically(tween(ms(300))) { it / 4 } + fadeOut(tween(ms(250)))

    NovaTheme(prefs.accent, prefs.surface) {
        Box(Modifier.fillMaxSize().background(Obsidian)) {
            NovaBackdrop(prefs.wallpaper, prefs.liveWallpaper && prefs.animSpeed != "off")

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
                        onAction = ::runAction,
                        onLaunch = ::launchTracked,
                        recent = recent,
                        timerText = timerText,
                        allApps = visibleApps,
                        dockPackages = dockPackages,
                        onToggleDock = ::toggleDock,
                        onHide = ::hideApp,
                        homeSignal = homeSignal
                    )
                }
            }

            AnimatedVisibility(
                visible = screen == Screen.Splash,
                enter = fadeIn(),
                exit = fadeOut(tween(ms(500)))
            ) { SplashScreen() }

            AnimatedVisibility(visible = screen == Screen.Drawer, enter = enter, exit = exit) {
                DrawerScreen(
                    apps = visibleApps,
                    dockPackages = dockPackages,
                    iconShape = prefs.iconShape,
                    iconSize = prefs.iconSize,
                    showLabels = prefs.showLabels,
                    onToggleDock = ::toggleDock,
                    onLaunch = ::launchTracked,
                    onHide = ::hideApp,
                    onClose = { screen = Screen.Home }
                )
            }

            AnimatedVisibility(visible = screen == Screen.Settings, enter = enter, exit = exit) {
                DesignStudioScreen(prefs = prefs, store = store, onBack = { screen = Screen.Home })
            }

            AnimatedVisibility(
                visible = screen == Screen.Control,
                enter = slideInVertically(tween(ms(350))) { -it } + fadeIn(tween(ms(250))),
                exit = slideOutVertically(tween(ms(300))) { -it } + fadeOut(tween(ms(250)))
            ) {
                ControlCenterScreen(onClose = { screen = Screen.Home })
            }

            AnimatedVisibility(
                visible = screen == Screen.Dashboard,
                enter = slideInHorizontally(tween(ms(350))) { it } + fadeIn(tween(ms(250))),
                exit = slideOutHorizontally(tween(ms(300))) { it } + fadeOut(tween(ms(250)))
            ) {
                DashboardScreen(
                    prefs = prefs,
                    apps = visibleApps,
                    onLaunch = ::launchTracked,
                    onClose = { screen = Screen.Home }
                )
            }

            AnimatedVisibility(
                visible = screen == Screen.Widgets,
                enter = slideInHorizontally(tween(ms(350))) { -it } + fadeIn(tween(ms(250))),
                exit = slideOutHorizontally(tween(ms(300))) { -it } + fadeOut(tween(ms(250)))
            ) {
                WidgetsScreen(
                    note = prefs.note,
                    onNote = { scope.launch { store.setNote(it) } },
                    timerLeft = timerLeft,
                    timerRunning = timerRunning,
                    timerPreset = timerPreset,
                    onTimerToggle = {
                        if (!timerRunning && timerLeft <= 0) timerLeft = timerPreset * 60
                        timerRunning = !timerRunning
                    },
                    onTimerReset = { timerRunning = false; timerLeft = timerPreset * 60 },
                    onTimerPreset = { timerPreset = it; timerRunning = false; timerLeft = it * 60 },
                    onClose = { screen = Screen.Home }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    prefs: NovaPrefs,
    dock: List<AppInfo>,
    onOpenDrawer: () -> Unit,
    onAction: (String) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    recent: List<AppInfo>,
    timerText: String?,
    allApps: List<AppInfo>,
    dockPackages: List<String>,
    onToggleDock: (AppInfo) -> Unit,
    onHide: (AppInfo) -> Unit,
    homeSignal: Int
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val latestPrefs by rememberUpdatedState(prefs)
    val latestAction by rememberUpdatedState(onAction)

    val classic = prefs.homeMode == "classic"
    val pages = remember(allApps) { allApps.chunked(CLASSIC_PER_PAGE) }
    val pageCount = maxOf(1, pages.size)
    val latestPageCount by rememberUpdatedState(pageCount)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0 && classic) pagerState.animateScrollToPage(0)
    }

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
                // Observes the gesture before children see it, without consuming it,
                // so the classic-mode pager can still scroll normally.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startPage = pagerState.currentPage
                    var dx = 0f
                    var dy = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                    }
                    val p = latestPrefs
                    if (abs(dy) > abs(dx)) {
                        if (dy < -120f) latestAction(p.gestureUp) else if (dy > 120f) latestAction(p.gestureDown)
                    } else if (p.homeMode != "classic") {
                        if (dx < -120f) latestAction(p.gestureLeft) else if (dx > 120f) latestAction(p.gestureRight)
                    } else {
                        // Classic mode: swiping past the first or last page opens the side pages.
                        if (dx > 120f && startPage == 0) latestAction(p.gestureRight)
                        else if (dx < -120f && startPage >= latestPageCount - 1) latestAction(p.gestureLeft)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { latestAction(latestPrefs.gestureLong) },
                    onDoubleTap = { latestAction(latestPrefs.gestureDouble) }
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        if (classic) {
            ClassicHome(
                pages = pages,
                pagerState = pagerState,
                prefs = prefs,
                time = time,
                date = date,
                timerText = timerText,
                dockPackages = dockPackages,
                onLaunch = onLaunch,
                onToggleDock = onToggleDock,
                onHide = onHide,
                modifier = Modifier.fillMaxSize().padding(top = 28.dp, bottom = (prefs.iconSize + 128).dp)
            )
        } else Column(Modifier.align(Alignment.TopStart).padding(top = 56.dp)) {
            Text(greeting, color = accent, fontSize = 12.sp, letterSpacing = 4.sp)
            Spacer(Modifier.height(4.dp))
            BasicText(
                time,
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(TextPrimary, TextPrimary, accent)),
                    fontSize = clockSizeSp(prefs.clockSize).sp,
                    fontWeight = clockWeight(prefs.clockWeight),
                    fontFamily = clockFamily(prefs.clockFont)
                )
            )
            Text(date, color = TextMuted, fontSize = 16.sp, letterSpacing = 1.sp)
            if (timerText != null) {
                Spacer(Modifier.height(4.dp))
                Text("FOCUS  $timerText", color = accent, fontSize = 12.sp, letterSpacing = 3.sp)
            }
            Spacer(Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    if (stats.charging) "Charging" else "Battery",
                    "${stats.battery}%", stats.battery / 100f, Modifier.weight(1f)
                )
                StatCard("Storage", "${stats.storagePct}%", stats.storagePct / 100f, Modifier.weight(1f))
                StatCard("Memory", "${stats.ramPct}%", stats.ramPct / 100f, Modifier.weight(1f))
            }
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(14.dp)) {
                    Text("RECENT", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(10.dp))
                    IconRow(recent, prefs.iconShape, onLaunch)
                }
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
                        app, prefs.iconSize.dp, prefs.iconShape,
                        Modifier.bounceClick { onLaunch(app) }
                    )
                }
            }
        }
    }
}
