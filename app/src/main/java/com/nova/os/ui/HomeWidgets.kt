package com.nova.os.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nova.os.data.AppInfo
import com.nova.os.data.CalEvent
import com.nova.os.data.NovaPrefs
import com.nova.os.data.NowPlaying
import com.nova.os.data.SysStats
import com.nova.os.data.fetchWeatherHere
import com.nova.os.data.hasListenerAccess
import com.nova.os.data.parseWeather
import com.nova.os.data.readNowPlaying
import com.nova.os.data.todaysEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/**
 * The home page content: header with clock and weather, system rings, today's agenda,
 * music player and frequently used apps. Widgets are dropped automatically, lowest priority
 * first, when the page is too short for all of them, so it never feels crowded.
 */
val HOME_SECTIONS = listOf("frequent", "upcoming", "stats", "music", "note")

fun homeSectionLabel(id: String) = when (id) {
    "frequent" -> "Frequently used apps"
    "upcoming" -> "Upcoming today"
    "stats" -> "System rings"
    "music" -> "Music player"
    else -> "Note card"
}

/** The saved widget order, always containing every section exactly once. */
fun normalizeHomeOrder(raw: String): List<String> {
    val saved = raw.split(",").map { it.trim() }.filter { it in HOME_SECTIONS }.distinct()
    return saved + HOME_SECTIONS.filter { it !in saved }
}

@Composable
fun HomeInfo(
    prefs: NovaPrefs,
    now: LocalDateTime,
    stats: SysStats,
    timerText: String?,
    allApps: List<AppInfo>,
    dock: List<AppInfo>,
    recent: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onAction: (String) -> Unit,
    onPutPref: (String, String) -> Unit
) {
    val accent = LocalAccent.current
    val frequent = remember(prefs.launchLog, allApps, dock, recent) {
        val used = frequentApps(prefs.launchLog, allApps, 4)
        (used + recent + dock + allApps).distinctBy { it.packageName }.take(4)
    }
    var moving by remember { mutableStateOf(false) }
    val orderIds = remember(prefs.homeOrder) { normalizeHomeOrder(prefs.homeOrder) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val avail = maxHeight
        val enabled = mapOf(
            "frequent" to prefs.homeFrequent,
            "upcoming" to prefs.homeUpcoming,
            "stats" to prefs.homeStats,
            "music" to prefs.homeMusic,
            "note" to (prefs.homeNote && prefs.note.isNotBlank())
        )
        val heights = mapOf(
            "frequent" to (if (moving) 150 else 104),
            "upcoming" to 112,
            "stats" to 84,
            "music" to 96,
            "note" to 92
        )
        val pinned = prefs.frequentPos == "bottom" && enabled["frequent"] == true
        var used = 150.dp + if (timerText != null) 18.dp else 0.dp
        if (pinned) used += ((heights["frequent"] ?: 104) + 14).dp
        val show = mutableSetOf<String>()
        for (id in orderIds) {
            if (id == "frequent" && pinned) continue
            val need = ((heights[id] ?: 100) + 14).dp
            if (enabled[id] == true && used + need <= avail) {
                show += id
                used += need
            }
        }
        val visible = orderIds.filter { it in show }
        val move: (String, Int) -> Unit = { id, delta ->
            val i = visible.indexOf(id)
            val j = i + delta
            if (i >= 0 && j in visible.indices) {
                val list = orderIds.toMutableList()
                val a = list.indexOf(id)
                val b = list.indexOf(visible[j])
                list[a] = visible[j]
                list[b] = id
                onPutPref("home_order", list.joinToString(","))
            }
        }

        val frequentCard: @Composable (Modifier) -> Unit = { mod ->
            FrequentCard(
                apps = frequent,
                prefs = prefs,
                onLaunch = onLaunch,
                modifier = mod,
                moving = moving,
                pinned = pinned,
                canUp = !pinned && visible.indexOf("frequent") > 0,
                canDown = !pinned && visible.indexOf("frequent") in 0 until visible.lastIndex,
                onStartMove = { moving = true },
                onUp = { move("frequent", -1) },
                onDown = { move("frequent", 1) },
                onTogglePin = { onPutPref("frequent_pos", if (pinned) "list" else "bottom") },
                onDone = { moving = false }
            )
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header: wordmark, then the clock and weather on one row, then the date.
            Column(Modifier.fillMaxWidth()) {
                Text("NOVA", color = accent, fontSize = 12.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val hours = now.format(DateTimeFormatter.ofPattern(if (prefs.use24h) "HH" else "hh"))
                    val minutes = now.format(DateTimeFormatter.ofPattern("mm"))
                    val baseSp = clockSizeSp(prefs.clockSize).toFloat()
                    val clockSp = if (prefs.homeWeather) minOf(baseSp * 0.8f, 72f) else baseSp
                    BasicText(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = TextPrimary)) { append(hours) }
                            withStyle(SpanStyle(color = TextPrimary)) { append(":") }
                            withStyle(SpanStyle(color = accent)) { append(minutes) }
                        },
                        style = TextStyle(
                            fontSize = clockSp.sp,
                            fontWeight = clockWeight(prefs.clockWeight),
                            fontFamily = clockFamily(prefs.clockFont)
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                    if (prefs.homeWeather) {
                        Spacer(Modifier.weight(1f))
                        WeatherCard(prefs, onPutPref)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CalendarGlyph(accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        now.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                        color = TextMuted, fontSize = 15.sp
                    )
                }
                if (timerText != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("FOCUS  $timerText", color = accent, fontSize = 12.sp, letterSpacing = 3.sp)
                }
            }

            visible.forEach { id ->
                when (id) {
                    "stats" -> StatsCard(stats)
                    "upcoming" -> UpcomingCard(prefs)
                    "music" -> MusicCard()
                    "frequent" -> frequentCard(Modifier)
                    "note" -> Column(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(24.dp))
                            .clickable { onAction("widgets") }
                            .padding(14.dp)
                    ) {
                        Text("NOTE", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            prefs.note.trim(), color = TextPrimary, fontSize = 14.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (pinned) frequentCard(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun CalendarGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke * 1.5f),
            size = Size(size.width - stroke, size.height - stroke * 2),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(stroke)
        )
        drawLine(color, Offset(size.width * 0.3f, 0f), Offset(size.width * 0.3f, stroke * 3), stroke)
        drawLine(color, Offset(size.width * 0.7f, 0f), Offset(size.width * 0.7f, stroke * 3), stroke)
        drawLine(color, Offset(0f, size.height * 0.4f), Offset(size.width, size.height * 0.4f), stroke)
    }
}

// ---------- Weather ----------

@Composable
private fun WeatherCard(prefs: NovaPrefs, onPutPref: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val granted = remember(tick) { hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val weather = remember(prefs.weather) { parseWeather(prefs.weather) }

    suspend fun refresh() {
        busy = true
        fetchWeatherHere(context)?.let { onPutPref("weather", it) }
        busy = false
    }

    LaunchedEffect(granted) {
        while (granted) {
            val w = parseWeather(prefs.weather)
            if (w == null || System.currentTimeMillis() - w.at > 30 * 60_000L) refresh()
            delay(10 * 60_000L)
        }
    }

    Column(
        Modifier
            .width(96.dp)
            .glass(RoundedCornerShape(20.dp))
            .clickable {
                if (!granted) launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                else if (!busy) scope.launch { refresh() }
            }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (weather != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(weather.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(4.dp))
                Text("${weather.temp}°", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Light)
            }
            Spacer(Modifier.height(2.dp))
            Text(weather.place, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text("🌤️", fontSize = 22.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    !granted -> "Show weather"
                    busy -> "Loading…"
                    else -> "Unavailable"
                },
                color = TextMuted, fontSize = 11.sp, maxLines = 1
            )
        }
    }
}

// ---------- System rings ----------

@Composable
private fun StatsCard(stats: SysStats) {
    val accent = LocalAccent.current
    val green = Color(0xFF2ED86A)
    val red = Color(0xFFFF5C5C)
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RingStat(
            "battery", stats.battery,
            if (stats.battery <= 20 && !stats.charging) red else green,
            if (stats.charging) "Charging" else "Battery", Modifier.weight(1f)
        )
        Box(Modifier.width(1.dp).height(36.dp).background(GlassBorder))
        RingStat("storage", stats.storagePct, accent, "Storage", Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(36.dp).background(GlassBorder))
        RingStat("memory", stats.ramPct, Nebula, "Memory", Modifier.weight(1f))
    }
}

@Composable
private fun RingStat(kind: String, percent: Int, color: Color, label: String, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(
                    color = Color(0x22FFFFFF), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke)
                )
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = 360f * percent.coerceIn(0, 100) / 100f,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                val cx = size.width / 2
                val cy = size.height / 2
                val line = 1.6.dp.toPx()
                when (kind) {
                    "battery" -> {
                        val bw = 11.dp.toPx()
                        val bh = 17.dp.toPx()
                        val left = cx - bw / 2
                        val top = cy - bh / 2 + 1.dp.toPx()
                        drawRoundRect(color, Offset(left, top), Size(bw, bh), CornerRadius(3.dp.toPx()), style = Stroke(line))
                        drawRoundRect(color, Offset(cx - 2.dp.toPx(), top - 2.dp.toPx()), Size(4.dp.toPx(), 2.dp.toPx()), CornerRadius(1.dp.toPx()))
                        val fill = (bh - 4.dp.toPx()) * percent.coerceIn(0, 100) / 100f
                        drawRoundRect(color, Offset(left + 2.dp.toPx(), top + bh - 2.dp.toPx() - fill), Size(bw - 4.dp.toPx(), fill), CornerRadius(1.dp.toPx()))
                    }
                    "storage" -> {
                        val w = 15.dp.toPx()
                        drawRoundRect(color, Offset(cx - w / 2, cy - 7.dp.toPx()), Size(w, 5.dp.toPx()), CornerRadius(2.5.dp.toPx()))
                        drawRoundRect(color, Offset(cx - w / 2, cy + 1.dp.toPx()), Size(w, 5.dp.toPx()), CornerRadius(2.5.dp.toPx()))
                    }
                    else -> {
                        val s = 12.dp.toPx()
                        drawRoundRect(color, Offset(cx - s / 2, cy - s / 2), Size(s, s), CornerRadius(2.dp.toPx()), style = Stroke(line))
                        drawRoundRect(color, Offset(cx - 2.5.dp.toPx(), cy - 2.5.dp.toPx()), Size(5.dp.toPx(), 5.dp.toPx()), CornerRadius(1.dp.toPx()))
                        for (d in listOf(-4f, 4f)) {
                            drawLine(color, Offset(cx + d.dp.toPx(), cy - s / 2 - 3.dp.toPx()), Offset(cx + d.dp.toPx(), cy - s / 2), line)
                            drawLine(color, Offset(cx + d.dp.toPx(), cy + s / 2), Offset(cx + d.dp.toPx(), cy + s / 2 + 3.dp.toPx()), line)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text("$percent%", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(label, color = TextMuted, fontSize = 11.sp)
        }
    }
}

// ---------- Upcoming today ----------

private class AgendaItem(val time: Long, val allDay: Boolean, val text: String, val isTask: Boolean)

@Composable
private fun UpcomingCard(prefs: NovaPrefs) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var tick by remember { mutableIntStateOf(0) }
    val granted = remember(tick) { hasPermission(context, Manifest.permission.READ_CALENDAR) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    var events by remember { mutableStateOf<List<CalEvent>>(emptyList()) }

    LaunchedEffect(granted) {
        while (granted) {
            events = withContext(Dispatchers.IO) { todaysEvents(context) }
            delay(5 * 60_000L)
        }
    }

    val zone = ZoneId.systemDefault()
    val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfDay = startOfDay + 24L * 60 * 60 * 1000
    val items = (events.map { AgendaItem(it.begin, it.allDay, it.title, false) } +
        prefs.tasks.filter { !it.done && (it.due ?: -1L) in startOfDay until endOfDay }
            .map { AgendaItem(it.due ?: 0L, false, it.title, true) })
        .sortedBy { if (it.allDay) 0L else it.time }
        .take(3)

    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .height(IntrinsicSize.Min)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(accent))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Upcoming Today", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            if (items.isEmpty()) {
                Text("No scheduled events", color = TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text("Enjoy your day!", color = TextMuted, fontSize = 12.sp)
            } else {
                items.forEach { item ->
                    val whenText = if (item.allDay) "All day" else
                        Instant.ofEpochMilli(item.time).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text(
                        "$whenText  ${item.text}" + if (item.isTask) "  · task" else "",
                        color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!granted) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Allow calendar to see events",
                    color = accent, fontSize = 12.sp,
                    modifier = Modifier.clickable { launcher.launch(Manifest.permission.READ_CALENDAR) }
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .clickable { openCalendar(context) }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) { Text("View Calendar →", color = accent, fontSize = 12.sp) }
    }
}

private fun openCalendar(context: Context) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = runCatching { context.startActivity(view) }.isSuccess
    if (!ok) {
        runCatching {
            context.startActivity(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

// ---------- Music ----------

@Composable
private fun TransportIcon(kind: String, color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        when (kind) {
            "play" -> drawPath(
                Path().apply {
                    moveTo(w * 0.28f, h * 0.15f); lineTo(w * 0.86f, h * 0.5f); lineTo(w * 0.28f, h * 0.85f); close()
                }, color
            )
            "pause" -> {
                drawRoundRect(color, Offset(w * 0.2f, h * 0.15f), Size(w * 0.22f, h * 0.7f), CornerRadius(3f))
                drawRoundRect(color, Offset(w * 0.58f, h * 0.15f), Size(w * 0.22f, h * 0.7f), CornerRadius(3f))
            }
            "next" -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.15f, h * 0.2f); lineTo(w * 0.65f, h * 0.5f); lineTo(w * 0.15f, h * 0.8f); close()
                    }, color
                )
                drawRoundRect(color, Offset(w * 0.72f, h * 0.2f), Size(w * 0.13f, h * 0.6f), CornerRadius(2f))
            }
            else -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.85f, h * 0.2f); lineTo(w * 0.35f, h * 0.5f); lineTo(w * 0.85f, h * 0.8f); close()
                    }, color
                )
                drawRoundRect(color, Offset(w * 0.15f, h * 0.2f), Size(w * 0.13f, h * 0.6f), CornerRadius(2f))
            }
        }
    }
}

@Composable
private fun MusicCard() {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var access by remember { mutableStateOf(hasListenerAccess(context)) }
    var playing by remember { mutableStateOf<NowPlaying?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            access = hasListenerAccess(context)
            val next = if (access) withContext(Dispatchers.Default) { readNowPlaying(context) } else null
            if (next == null || !next.sameAs(playing) || playing?.art == null) playing = next
            delay(1500)
        }
    }

    val np = playing
    val enabled = np != null
    val dim = if (enabled) 1f else 0.35f

    fun openPlayer() {
        when {
            !access -> runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            np != null -> {
                val sent = runCatching { np.controller.sessionActivity?.send() }.isSuccess && np.controller.sessionActivity != null
                if (!sent) {
                    context.packageManager.getLaunchIntentForPackage(np.pkg)?.let {
                        runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
            else -> runCatching {
                context.startActivity(
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .clickable { openPlayer() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.8f), Nebula.copy(alpha = 0.8f)))),
            contentAlignment = Alignment.Center
        ) {
            val art = np?.art
            if (art != null) {
                Image(bitmap = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("♪", color = Color.White, fontSize = 30.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                np?.title ?: "No Music Playing",
                color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                when {
                    np != null -> np.artist.ifBlank { "Now playing" }
                    !access -> "Tap to enable music controls"
                    else -> "Tap to open player"
                },
                color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        val tint = TextPrimary.copy(alpha = dim)
        Box(
            Modifier.size(36.dp).clickable(enabled = enabled) { np?.controller?.transportControls?.skipToPrevious() },
            contentAlignment = Alignment.Center
        ) { TransportIcon("prev", tint) }
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(1.5.dp, accent.copy(alpha = dim), CircleShape)
                .clickable(enabled = enabled) {
                    val c = np?.controller?.transportControls
                    if (np?.playing == true) c?.pause() else c?.play()
                },
            contentAlignment = Alignment.Center
        ) { TransportIcon(if (np?.playing == true) "pause" else "play", TextPrimary.copy(alpha = dim)) }
        Box(
            Modifier.size(36.dp).clickable(enabled = enabled) { np?.controller?.transportControls?.skipToNext() },
            contentAlignment = Alignment.Center
        ) { TransportIcon("next", tint) }
    }
}

// ---------- Frequently used ----------

@Composable
private fun MoveChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassFill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) TextPrimary else TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun FrequentCard(
    apps: List<AppInfo>,
    prefs: NovaPrefs,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier,
    moving: Boolean,
    pinned: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onStartMove: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onTogglePin: () -> Unit,
    onDone: () -> Unit
) {
    if (apps.isEmpty()) return
    val accent = LocalAccent.current
    val iconSize = prefs.iconSize.coerceIn(44, 60)
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .glass(shape)
            .then(if (moving) Modifier.border(1.5.dp, accent, shape) else Modifier)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onStartMove() }) }
            .padding(vertical = 14.dp, horizontal = 8.dp)
    ) {
        if (moving) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (pinned) "PINNED ABOVE SEARCH" else "MOVE ROW", color = accent, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                if (!pinned) {
                    MoveChip("▲", canUp, onUp)
                    Spacer(Modifier.width(6.dp))
                    MoveChip("▼", canDown, onDown)
                    Spacer(Modifier.width(6.dp))
                }
                MoveChip(if (pinned) "Unpin" else "Pin ↓", true, onTogglePin)
                Spacer(Modifier.width(6.dp))
                MoveChip("Done", true, onDone)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            apps.forEach { app ->
                Column(
                    Modifier.weight(1f).bounceClick(onLongClick = onStartMove) {
                        if (moving) onDone() else onLaunch(app)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NovaIcon(app, iconSize.dp, prefs.iconShape)
                    if (prefs.showLabels) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            app.label, color = TextPrimary, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            repeat(4 - apps.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
