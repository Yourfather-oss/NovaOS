package com.nova.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.data.AppInfo
import com.nova.os.data.NovaPrefs
import com.nova.os.data.openAppInfo

private const val CLASSIC_COLUMNS = 4
private const val CLASSIC_ROWS = 5
const val CLASSIC_PER_PAGE = CLASSIC_COLUMNS * CLASSIC_ROWS

private fun letterOf(app: AppInfo): String =
    app.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"

/** Classic mode: every app lives on the home screen, spread over automatically created pages. */
@Composable
fun ClassicHome(
    pages: List<List<AppInfo>>,
    pagerState: PagerState,
    prefs: NovaPrefs,
    time: String,
    date: String,
    timerText: String?,
    dockPackages: List<String>,
    onLaunch: (AppInfo) -> Unit,
    onToggleDock: (AppInfo) -> Unit,
    onHide: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    val current = pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val range = pages.getOrNull(current)?.let { p ->
        if (p.isEmpty()) "" else "${letterOf(p.first())} – ${letterOf(p.last())}"
    } ?: ""

    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                time,
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(TextPrimary, TextPrimary, accent)),
                    fontSize = (clockSizeSp(prefs.clockSize) * 0.55f).sp,
                    fontWeight = clockWeight(prefs.clockWeight),
                    fontFamily = clockFamily(prefs.clockFont)
                )
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(date, color = TextMuted, fontSize = 13.sp, letterSpacing = 1.sp)
                if (timerText != null) {
                    Text("FOCUS  $timerText", color = accent, fontSize = 11.sp, letterSpacing = 3.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val pageApps = pages.getOrNull(index).orEmpty()
            val rows = pageApps.chunked(CLASSIC_COLUMNS)
            Column(Modifier.fillMaxSize()) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        row.forEach { app ->
                            ClassicCell(
                                app, prefs, dockPackages, onLaunch, onToggleDock, onHide,
                                Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        repeat(CLASSIC_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                repeat(CLASSIC_ROWS - rows.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(range, color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == current) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == current) accent else GlassBorder)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassicCell(
    app: AppInfo,
    prefs: NovaPrefs,
    dockPackages: List<String>,
    onLaunch: (AppInfo) -> Unit,
    onToggleDock: (AppInfo) -> Unit,
    onHide: (AppInfo) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val pinned = app.packageName in dockPackages
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .bounceClick(onLongClick = { menu = true }) { onLaunch(app) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NovaIcon(app, prefs.iconSize.dp, prefs.iconShape)
            if (prefs.showLabels) {
                Spacer(Modifier.height(5.dp))
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
            DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; openAppInfo(context, app) })
            DropdownMenuItem(text = { Text("Hide app") }, onClick = { menu = false; onHide(app) })
        }
    }
}
