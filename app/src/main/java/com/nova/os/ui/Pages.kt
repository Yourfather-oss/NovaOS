package com.nova.os.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.data.AppInfo
import com.nova.os.data.NovaPrefs
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

// ---------- Insights (computed from NOVA's own launch history) ----------

fun recentApps(log: List<Pair<String, Long>>, apps: List<AppInfo>, n: Int): List<AppInfo> =
    log.asReversed().map { it.first }.distinct()
        .mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
        .take(n)

fun frequentApps(log: List<Pair<String, Long>>, apps: List<AppInfo>, n: Int): List<AppInfo> =
    log.groupingBy { it.first }.eachCount().entries
        .sortedByDescending { it.value }
        .mapNotNull { e -> apps.firstOrNull { it.packageName == e.key } }
        .take(n)

fun usualAround(log: List<Pair<String, Long>>, apps: List<AppInfo>, hour: Int, n: Int): List<AppInfo> {
    val zone = ZoneId.systemDefault()
    return log.filter { (_, ts) ->
        val h = Instant.ofEpochMilli(ts).atZone(zone).hour
        val d = abs(h - hour)
        minOf(d, 24 - d) <= 1
    }.groupingBy { it.first }.eachCount().entries
        .sortedByDescending { it.value }
        .mapNotNull { e -> apps.firstOrNull { it.packageName == e.key } }
        .take(n)
}

fun launchesToday(log: List<Pair<String, Long>>): Int {
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return log.count { it.second >= start }
}

// ---------- Shared pieces ----------

@Composable
fun IconRow(apps: List<AppInfo>, iconShape: String, onLaunch: (AppInfo) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        apps.forEach { app ->
            Column(
                Modifier.weight(1f).bounceClick { onLaunch(app) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NovaIcon(app, 48.dp, iconShape)
                Spacer(Modifier.height(6.dp))
                Text(
                    app.label, color = TextPrimary, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
            }
        }
        repeat(5 - apps.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(16.dp)) {
        Text(title, color = TextMuted, fontSize = 11.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun PillButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .glass(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = TextPrimary, fontSize = 14.sp) }
}

private fun pageModifier(): Modifier = Modifier
    .fillMaxSize()
    .background(Brush.verticalGradient(listOf(Obsidian.copy(alpha = 0.90f), Obsidian.copy(alpha = 0.97f))))

// ---------- NOVA Intelligence dashboard (swipe left on home) ----------

@Composable
fun DashboardScreen(
    prefs: NovaPrefs,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    val accent = LocalAccent.current
    val log = prefs.launchLog
    val hour = remember { LocalDateTime.now().hour }
    val today = remember(log) { launchesToday(log) }
    val usual = remember(log, apps) { usualAround(log, apps, hour, 5) }
    val recent = remember(log, apps) { recentApps(log, apps, 5) }
    val frequent = remember(log, apps) { frequentApps(log, apps, 5) }

    Column(
        pageModifier()
            .blockTouches()
            .horizontalSwipe(onSwipeRight = onClose)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("NOVA INTELLIGENCE", color = accent, fontSize = 12.sp, letterSpacing = 4.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            if (log.isEmpty()) "Open apps through NOVA and I'll learn your routine."
            else "You've opened $today ${if (today == 1) "app" else "apps"} today.",
            color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Light
        )
        if (frequent.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Most used: ${frequent.first().label}", color = TextMuted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(24.dp))

        Section("USUAL AROUND THIS TIME") {
            if (usual.isEmpty()) Text("Not enough history yet", color = TextMuted, fontSize = 13.sp)
            else IconRow(usual, prefs.iconShape, onLaunch)
        }
        Spacer(Modifier.height(14.dp))
        Section("RECENT") {
            if (recent.isEmpty()) Text("Nothing yet", color = TextMuted, fontSize = 13.sp)
            else IconRow(recent, prefs.iconShape, onLaunch)
        }
        Spacer(Modifier.height(14.dp))
        Section("MOST USED") {
            if (frequent.isEmpty()) Text("Nothing yet", color = TextMuted, fontSize = 13.sp)
            else IconRow(frequent, prefs.iconShape, onLaunch)
        }
        Spacer(Modifier.height(24.dp))
        Text("Swipe right to go back", color = TextMuted, fontSize = 11.sp, letterSpacing = 2.sp)
    }
}

// ---------- Widget space (swipe right on home) ----------

@Composable
fun WidgetsScreen(
    note: String,
    onNote: (String) -> Unit,
    timerLeft: Int,
    timerRunning: Boolean,
    timerPreset: Int,
    onTimerToggle: () -> Unit,
    onTimerReset: () -> Unit,
    onTimerPreset: (Int) -> Unit,
    onClose: () -> Unit
) {
    val accent = LocalAccent.current
    var text by remember { mutableStateOf(note) }
    LaunchedEffect(text) {
        delay(500)
        if (text != note) onNote(text)
    }

    Column(
        pageModifier()
            .blockTouches()
            .horizontalSwipe(onSwipeLeft = onClose)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("NOVA WIDGETS", color = accent, fontSize = 12.sp, letterSpacing = 4.sp)
        Spacer(Modifier.height(20.dp))

        Section("QUICK NOTE") {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("Jot something down…", color = TextMuted, fontSize = 16.sp)
                        inner()
                    }
                }
            )
        }

        Spacer(Modifier.height(14.dp))
        Section("FOCUS TIMER") {
            val m = timerLeft / 60
            val s = timerLeft % 60
            Text(
                "%02d:%02d".format(m, s),
                color = if (timerRunning) accent else TextPrimary,
                fontSize = 56.sp, fontWeight = FontWeight.Thin
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(5, 15, 25).forEach { minutes ->
                    OptionChip("$minutes min", timerPreset == minutes) { onTimerPreset(minutes) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(if (timerRunning) "Pause" else "Start", Modifier.weight(1f), onTimerToggle)
                PillButton("Reset", Modifier.weight(1f), onTimerReset)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Swipe left to go back", color = TextMuted, fontSize = 11.sp, letterSpacing = 2.sp)
    }
}
