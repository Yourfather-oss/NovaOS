package com.nova.os.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.data.AppInfo
import com.nova.os.data.openAppInfo
import kotlin.math.abs

// ---------- Universal search helpers ----------

private val settingsShortcuts: List<Pair<String, String>> = listOf(
    "Wi-Fi settings" to Settings.ACTION_WIFI_SETTINGS,
    "Bluetooth settings" to Settings.ACTION_BLUETOOTH_SETTINGS,
    "Display and brightness" to Settings.ACTION_DISPLAY_SETTINGS,
    "Sound and volume" to Settings.ACTION_SOUND_SETTINGS,
    "Battery saver" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
    "Storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
    "Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
    "Airplane mode" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
    "Mobile network" to Settings.ACTION_WIRELESS_SETTINGS,
    "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
    "Accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
    "Date and time" to Settings.ACTION_DATE_SETTINGS,
    "Language" to Settings.ACTION_LOCALE_SETTINGS,
    "Security" to Settings.ACTION_SECURITY_SETTINGS,
    "Developer options" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
)

private class Parser(private val s: String) {
    private var i = 0

    fun parse(): Double {
        val v = expr()
        skip()
        if (i != s.length) throw IllegalArgumentException()
        return v
    }

    private fun skip() { while (i < s.length && s[i] == ' ') i++ }

    private fun peek(): Char? {
        skip()
        return if (i < s.length) s[i] else null
    }

    private fun expr(): Double {
        var v = term()
        while (true) {
            val c = peek()
            if (c == '+') { i++; v += term() }
            else if (c == '-') { i++; v -= term() }
            else return v
        }
    }

    private fun term(): Double {
        var v = power()
        while (true) {
            val c = peek()
            if (c == '*') { i++; v *= power() }
            else if (c == '/') { i++; v /= power() }
            else return v
        }
    }

    private fun power(): Double {
        val b = unary()
        if (peek() == '^') {
            i++
            return Math.pow(b, power())
        }
        return b
    }

    private fun unary(): Double {
        val c = peek()
        if (c == '-') { i++; return -unary() }
        if (c == '+') { i++; return unary() }
        return postfix()
    }

    private fun postfix(): Double {
        var v = atom()
        while (peek() == '%') { i++; v /= 100.0 }
        return v
    }

    private fun atom(): Double {
        val c = peek() ?: throw IllegalArgumentException()
        if (c == '(') {
            i++
            val v = expr()
            if (peek() != ')') throw IllegalArgumentException()
            i++
            return v
        }
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        if (start == i) throw IllegalArgumentException()
        return s.substring(start, i).toDouble()
    }
}

/** Returns the answer if the query is a maths expression such as 12*(3+4), otherwise null. */
fun evalExpression(input: String): String? {
    val q = input.trim()
    if (!Regex("^[0-9+\\-*/x×÷^().%\\s]+$").matches(q)) return null
    if (!q.any { it.isDigit() } || !q.any { it in "+-*/x×÷^%" }) return null
    return runCatching {
        val v = Parser(q.replace('x', '*').replace('×', '*').replace('÷', '/')).parse()
        when {
            v.isNaN() || v.isInfinite() -> null
            v == Math.floor(v) && abs(v) < 1e15 -> v.toLong().toString()
            else -> "%.6f".format(v).trimEnd('0').trimEnd('.')
        }
    }.getOrNull()
}

private fun openSystemPage(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun webSearch(context: Context, query: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .glass(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 16.sp)
        Text(subtitle, color = TextMuted, fontSize = 11.sp)
    }
}

// ---------- Drawer ----------

private val categoryOrder = listOf("Finance", "Social", "Productivity", "Media", "Games", "Tools", "Other")

@Composable
fun DrawerScreen(
    apps: List<AppInfo>,
    dockPackages: List<String>,
    iconShape: String,
    iconSize: Int,
    showLabels: Boolean,
    launchLog: List<Pair<String, Long>>,
    onToggleDock: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onHide: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val clipboard = LocalClipboardManager.current

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }

    val categories = remember(apps) {
        listOf("All") + categoryOrder.filter { c -> apps.any { it.category == c } }
    }
    val searching = query.isNotBlank()
    val frequent = remember(launchLog, apps) { frequentApps(launchLog, apps, 4) }
    val filtered = remember(apps, query, category) {
        when {
            searching -> apps.filter { it.label.contains(query, ignoreCase = true) }
            category != "All" -> apps.filter { it.category == category }
            else -> apps
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { app ->
            app.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
        }.toSortedMap()
    }
    val calc = remember(query) { evalExpression(query) }
    val settingsHits = remember(query) {
        if (query.trim().length >= 2) settingsShortcuts.filter { it.first.contains(query.trim(), ignoreCase = true) }
        else emptyList()
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
        if (!searching) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { c -> OptionChip(c, category == c) { category = c } }
            }
            Spacer(Modifier.height(4.dp))
        }

        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (searching) {
                if (calc != null) {
                    item(key = "calc", span = { GridItemSpan(maxLineSpan) }) {
                        ResultRow("= $calc", "Tap to copy") { clipboard.setText(AnnotatedString(calc)) }
                    }
                }
                settingsHits.forEach { (title, action) ->
                    item(key = "set_$title", span = { GridItemSpan(maxLineSpan) }) {
                        ResultRow(title, "Open in Android settings") { openSystemPage(context, action); onClose() }
                    }
                }
                item(key = "web", span = { GridItemSpan(maxLineSpan) }) {
                    ResultRow("Search the web for \"${query.trim()}\"", "Opens your browser") {
                        webSearch(context, query.trim())
                    }
                }
            }

            grouped.forEach { (letter, list) ->
                if (!searching) {
                    item(key = "header_$letter", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            letter.toString(), color = accent, fontSize = 13.sp, letterSpacing = 3.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp)
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
                                .bounceClick(onLongClick = { menu = true }) { onLaunch(app) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NovaIcon(app, iconSize.dp, iconShape)
                            if (showLabels) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    app.label, color = TextPrimary, fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                                )
                            }
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
                            DropdownMenuItem(
                                text = { Text("Hide app") },
                                onClick = { menu = false; onHide(app) }
                            )
                        }
                    }
                }
            }
        }

        if (!searching && frequent.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                frequent.forEach { app ->
                    Box(Modifier.clickable { onLaunch(app) }, contentAlignment = Alignment.Center) {
                        NovaIcon(app, 48.dp, iconShape)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.fillMaxWidth().height(52.dp).glass(RoundedCornerShape(26.dp)),
            decorationBox = { inner ->
                Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (query.isEmpty()) Text("Search apps, settings, maths, web", color = TextMuted, fontSize = 16.sp)
                    inner()
                }
            }
        )
    }
}

/** Search results shown in place on the classic home screen, without the A–Z drawer. */
@Composable
fun InlineSearchResults(
    query: String,
    apps: List<AppInfo>,
    columns: Int,
    iconSize: Int,
    iconShape: String,
    iconFinish: String,
    showLabels: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val q = query.trim()
    val matches = remember(apps, q) { apps.filter { it.label.contains(q, ignoreCase = true) } }
    val calc = remember(q) { evalExpression(q) }
    val settingsHits = remember(q) {
        if (q.length >= 2) settingsShortcuts.filter { it.first.contains(q, ignoreCase = true) } else emptyList()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .background(Obsidian.copy(alpha = 0.90f), RoundedCornerShape(24.dp))
            .glass(RoundedCornerShape(24.dp))
            .padding(12.dp)
    ) {
        if (calc != null) {
            item(key = "calc", span = { GridItemSpan(maxLineSpan) }) {
                ResultRow("= $calc", "Tap to copy") { clipboard.setText(AnnotatedString(calc)) }
            }
        }
        settingsHits.forEach { (title, action) ->
            item(key = "set_$title", span = { GridItemSpan(maxLineSpan) }) {
                ResultRow(title, "Open in Android settings") { openSystemPage(context, action); onDone() }
            }
        }
        items(matches, key = { it.component.flattenToString() }) { app ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 2.dp)
                    .bounceClick { onLaunch(app) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NovaIcon(app, iconSize.dp, iconShape, finish = iconFinish)
                if (showLabels) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        app.label, color = TextPrimary, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                    )
                }
            }
        }
        item(key = "web", span = { GridItemSpan(maxLineSpan) }) {
            ResultRow("Search the web for \"$q\"", "Opens your browser") { webSearch(context, q) }
        }
    }
}
