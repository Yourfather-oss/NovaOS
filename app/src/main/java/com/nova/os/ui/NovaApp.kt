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
import com.nova.os.Reminders
import com.nova.os.data.AppInfo
import com.nova.os.data.Task
import com.nova.os.data.ClassicLayout
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
    var editingPages by remember { mutableStateOf(false) }
    var searchSignal by remember { mutableIntStateOf(0) }
    BackHandler(enabled = editingPages && screen == Screen.Home) { editingPages = false }

    val visibleApps = remember(apps, prefs.hidden) { apps.filter { it.packageName !in prefs.hidden } }
    val dockPackages = prefs.dock ?: defaultDock(visibleApps)
    val dockApps = dockPackages.mapNotNull { pkg -> visibleApps.firstOrNull { it.packageName == pkg } }

    fun toggleDock(app: AppInfo) {
        val current = dockPackages.toMutableList()
        if (!current.remove(app.packageName) && current.size < 10) current.add(app.packageName)
        scope.launch { store.setDock(current) }
    }

    fun launchTracked(app: AppInfo) {
        launchApp(context, app)
        scope.launch { store.logLaunch(app.packageName) }
    }

    fun runAction(action: String) {
        when (action) {
            "drawer" -> {
                // Classic mode has no A–Z drawer: the same gesture focuses the home search instead.
                if (prefs.homeMode == "classic") {
                    searchSignal++
                } else {
                    screen = Screen.Drawer
                }
            }
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

    fun saveLayout(l: ClassicLayout) {
        scope.launch { store.setClassicLayout(l.pages, l.unplaced) }
    }

    fun hideApp(app: AppInfo) {
        val next = (prefs.hidden + app.packageName).distinct().joinToString(",")
        scope.launch { store.put("hidden", next) }
    }

    val recent = remember(prefs.launchLog, visibleApps) { recentApps(prefs.launchLog, visibleApps, 5) }

    var timerPreset by remember { mutableIntStateOf(25) }
    var timerLeft by remember { mutableIntStateOf(25 * 60) }
    var timerRunning by remember { mutableStateOf(false) }
    var focusTitle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(timerRunning) {
        if (!timerRunning) return@LaunchedEffect
        while (timerRunning && timerLeft > 0) {
            delay(1000)
            timerLeft--
        }
        if (timerLeft <= 0) {
            timerRunning = false
            scope.launch { store.logFocus() }
            Reminders.notifyNow(
                context, 9001, "Focus session complete",
                focusTitle ?: "Nice work. Take a short break."
            )
            focusTitle = null
        }
    }

    fun saveTasks(next: List<Task>) {
        val now = System.currentTimeMillis()
        prefs.tasks
            .filter { old -> next.none { it.id == old.id && !it.done } }
            .forEach { Reminders.cancel(context, it.id) }
        next.filter { !it.done && (it.due ?: 0L) > now }.forEach { Reminders.schedule(context, it) }
        scope.launch { store.setTasks(next) }
    }

    // Re-arm reminders after a restart or update.
    LaunchedEffect(prefs.tasks) {
        val now = System.currentTimeMillis()
        prefs.tasks.filter { !it.done && (it.due ?: 0L) > now }.forEach { Reminders.schedule(context, it) }
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

    NovaTheme(prefs.accent, prefs.surface, prefs.iconFinish) {
        Box(Modifier.fillMaxSize().background(Obsidian)) {
            NovaBackdrop(prefs.wallpaper, prefs.liveWallpaper && prefs.animSpeed != "off", prefs.wallpaperDim, prefs.wallpaperRev)

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
                        homeSignal = homeSignal,
                        editing = editingPages,
                        onEditingChange = { editingPages = it },
                        onSaveLayout = ::saveLayout,
                        searchSignal = searchSignal,
                        onPutPref = { k, v -> scope.launch { store.put(k, v) } }
                    )
                }
            }

            AnimatedVisibility(
                visible = screen == Screen.Splash,
                enter = fadeIn(),
                exit = fadeOut(tween(ms(500)))
            ) { SplashScreen() }

            AnimatedVisibility(visible = screen == Screen.Drawer && prefs.homeMode != "classic", enter = enter, exit = exit) {
                DrawerScreen(
                    apps = visibleApps,
                    dockPackages = dockPackages,
                    iconShape = prefs.iconShape,
                    iconSize = prefs.iconSize,
                    showLabels = prefs.showLabels,
                    launchLog = prefs.launchLog,
                    onToggleDock = ::toggleDock,
                    onLaunch = ::launchTracked,
                    onHide = ::hideApp,
                    onClose = { screen = Screen.Home }
                )
            }

            AnimatedVisibility(visible = screen == Screen.Settings, enter = enter, exit = exit) {
                DesignStudioScreen(
                    prefs = prefs,
                    store = store,
                    apps = visibleApps,
                    onEditPages = { editingPages = true; screen = Screen.Home },
                    onBack = { screen = Screen.Home }
                )
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
                    prefs = prefs,
                    onNote = { scope.launch { store.setNote(it) } },
                    timerLeft = timerLeft,
                    timerRunning = timerRunning,
                    timerPreset = timerPreset,
                    timerLabel = focusTitle,
                    onTimerToggle = {
                        if (!timerRunning && timerLeft <= 0) timerLeft = timerPreset * 60
                        timerRunning = !timerRunning
                    },
                    onTimerReset = { timerRunning = false; timerLeft = timerPreset * 60; focusTitle = null },
                    onTimerPreset = { timerPreset = it; timerRunning = false; timerLeft = it * 60; focusTitle = null },
                    onTasksChange = ::saveTasks,
                    onFocusTask = { task ->
                        focusTitle = task.title
                        timerPreset = 25
                        timerLeft = 25 * 60
                        timerRunning = true
                    },
                    onAiSettings = { key, model ->
                        scope.launch {
                            store.put("ai_key", key)
                            store.put("ai_model", model)
                        }
                    },
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
