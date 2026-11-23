package com.nova.os.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.data.AppInfo
import com.nova.os.data.ClassicLayout
import com.nova.os.data.NovaPrefs
import com.nova.os.data.SysStats
import com.nova.os.data.gridSpec
import com.nova.os.data.readStats
import com.nova.os.data.reconcile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
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
    homeSignal: Int,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSaveLayout: (ClassicLayout) -> Unit,
    searchSignal: Int,
    onPutPref: (String, String) -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val latestPrefs by rememberUpdatedState(prefs)
    val latestAction by rememberUpdatedState(onAction)

    val classic = prefs.homeMode == "classic"
    val isEditing = editing && classic
    val latestEditing by rememberUpdatedState(isEditing)

    // Classic mode searches in place; the A–Z drawer is only used by NOVA mode.
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val searching = classic && query.isNotBlank()
    LaunchedEffect(classic) { query = "" }
    LaunchedEffect(searchSignal) {
        if (searchSignal > 0 && classic) runCatching { focusRequester.requestFocus() }
    }
    BackHandler(enabled = query.isNotEmpty()) {
        query = ""
        focusManager.clearFocus()
    }
    val launchAndClear: (AppInfo) -> Unit = {
        query = ""
        focusManager.clearFocus()
        onLaunch(it)
    }

    val grid = gridSpec(prefs.gridPreset)
    val layout = remember(prefs.classicPages, prefs.classicUnplaced, allApps, grid) {
        reconcile(prefs.classicPages, prefs.classicUnplaced, allApps, grid.perPage)
    }
    val appsByPkg = remember(allApps) { allApps.associateBy { it.packageName } }
    val pageCount = layout.pages.size + 1
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

    // Clock and widgets: the NOVA home page, and page 0 in classic mode.
    val infoBlock: @Composable () -> Unit = {
        HomeInfo(
            prefs = prefs,
            now = now,
            stats = stats,
            timerText = timerText,
            allApps = allApps,
            dock = dock,
            recent = recent,
            onLaunch = launchAndClear,
            onAction = onAction,
            onPutPref = onPutPref
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Watches the gesture before children see it, without consuming it,
                // so the classic-mode pager and the dock can still scroll normally.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startPage = pagerState.currentPage
                    val dockZone = (latestPrefs.iconSize + 100).dp.toPx()
                    val startedInDock = down.position.y > size.height - dockZone
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
                    if (latestEditing) return@awaitEachGesture
                    val p = latestPrefs
                    if (abs(dy) > abs(dx)) {
                        if (dy < -120f) latestAction(p.gestureUp) else if (dy > 120f) latestAction(p.gestureDown)
                    } else if (startedInDock) {
                        // Horizontal swipes on the dock scroll the dock.
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
                    onLongPress = { if (!latestEditing) latestAction(latestPrefs.gestureLong) },
                    onDoubleTap = { if (!latestEditing) latestAction(latestPrefs.gestureDouble) }
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        if (classic) {
            ClassicHome(
                layout = layout,
                appsByPkg = appsByPkg,
                pagerState = pagerState,
                prefs = prefs,
                grid = grid,
                editing = isEditing,
                homePage = { Box(Modifier.fillMaxSize().padding(top = 20.dp)) { infoBlock() } },
                dockPackages = dockPackages,
                onLaunch = launchAndClear,
                onToggleDock = onToggleDock,
                onHide = onHide,
                onLayout = onSaveLayout,
                onEditingChange = onEditingChange,
                modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = (prefs.iconSize + 190).dp)
            )
        } else {
            Box(Modifier.fillMaxSize().padding(top = 36.dp, bottom = (prefs.iconSize + 190).dp)) { infoBlock() }
        }

        if (searching) {
            InlineSearchResults(
                query = query,
                apps = allApps,
                columns = grid.cols,
                iconSize = prefs.iconSize,
                iconShape = prefs.iconShape,
                iconFinish = prefs.iconFinish,
                showLabels = prefs.showLabels,
                onLaunch = launchAndClear,
                onDone = { query = ""; focusManager.clearFocus() },
                modifier = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 84.dp)
            )
        }

        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
            // Keep recently launched apps directly above search, separate from the dock.
            if (!searching && recent.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recent.take(5).forEach { app ->
                        Column(
                            Modifier.weight(1f).bounceClick { launchAndClear(app) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NovaIcon(app, 40.dp, prefs.iconShape)
                            if (prefs.showLabels) {
                                Spacer(Modifier.height(3.dp))
                                Text(app.label, color = TextPrimary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (classic) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .glass(RoundedCornerShape(26.dp))
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("Search apps, settings, maths, web", color = TextMuted, fontSize = 15.sp)
                            }
                            inner()
                        }
                    }
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .glass(RoundedCornerShape(26.dp))
                        .clickable { onOpenDrawer() }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) { Text("Search apps", color = TextMuted, fontSize = 15.sp) }
            }

            Spacer(Modifier.height(16.dp))

            val manyDock = dock.size * (prefs.iconSize + 6) > 312
            val dockScroll = rememberScrollState()
            val dockBase = when (prefs.dockStyle) {
                "none" -> Modifier.fillMaxWidth()
                "flat" -> Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp))
                else -> Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp))
            }
            if (!searching) Row(
                dockBase
                    .then(if (manyDock) Modifier.horizontalScroll(dockScroll) else Modifier)
                    .padding(vertical = 14.dp, horizontal = if (manyDock) 14.dp else 0.dp),
                horizontalArrangement = if (manyDock) Arrangement.spacedBy(16.dp) else Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dock.isEmpty()) {
                    Text("Long-press an app in the drawer to pin it here", color = TextMuted, fontSize = 12.sp)
                }
                dock.forEach { app ->
                    NovaIcon(
                        app, prefs.iconSize.dp, prefs.iconShape,
                        Modifier.bounceClick { launchAndClear(app) }
                    )
                }
            }
        }
    }
}
