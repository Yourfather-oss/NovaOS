package com.nova.os.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.os.data.AppInfo
import com.nova.os.data.ClassicLayout
import com.nova.os.data.GridSpec
import com.nova.os.data.NovaPrefs
import com.nova.os.data.addPage
import com.nova.os.data.deletePage
import com.nova.os.data.movePlaced
import com.nova.os.data.openAppInfo
import com.nova.os.data.organize
import com.nova.os.data.removeFromPages
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun letterOf(app: AppInfo): String =
    app.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"

/**
 * Classic mode. Page 0 is a fixed page for the clock and widgets; the pages after it hold every app.
 * In edit mode the user can add or delete pages and move apps between positions and pages.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClassicHome(
    layout: ClassicLayout,
    appsByPkg: Map<String, AppInfo>,
    pagerState: PagerState,
    prefs: NovaPrefs,
    grid: GridSpec,
    editing: Boolean,
    homePage: @Composable () -> Unit,
    dockPackages: List<String>,
    onLaunch: (AppInfo) -> Unit,
    onToggleDock: (AppInfo) -> Unit,
    onHide: (AppInfo) -> Unit,
    onLayout: (ClassicLayout) -> Unit,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current
    var selected by remember { mutableStateOf<String?>(null) }
    val sel = selected
    val current = pagerState.currentPage.coerceIn(0, layout.pages.size)
    val allApps = remember(appsByPkg) { appsByPkg.values.toList() }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun place(next: ClassicLayout?) {
        if (next == null) toast("That page is full") else onLayout(next)
        selected = null
    }

    LaunchedEffect(editing) { if (!editing) selected = null }

    Column(modifier) {
        if (editing) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolPill("Done") { onEditingChange(false) }
                ToolPill("+ Page") {
                    onLayout(addPage(layout))
                    scope.launch {
                        delay(250)
                        pagerState.animateScrollToPage(layout.pages.size + 1)
                    }
                }
                ToolPill("Delete page") {
                    if (current == 0) {
                        toast("The clock and widgets page can't be deleted")
                    } else {
                        val moved = layout.pages[current - 1].size
                        onLayout(deletePage(layout, current - 1))
                        selected = null
                        toast(if (moved > 0) "$moved apps moved to Unplaced" else "Page deleted")
                    }
                }
                ToolPill("Sort A–Z") { onLayout(organize(allApps, false, grid.perPage)); toast("Sorted A–Z") }
                ToolPill("By category") { onLayout(organize(allApps, true, grid.perPage)); toast("Grouped by category") }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (sel == null) "Tap an app to pick it up, then tap where it should go. Swipe to change page."
                else "Moving ${appsByPkg[sel]?.label ?: ""}: tap an app to place it before that app, or an empty spot for the end of the page.",
                color = TextMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(6.dp))
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            if (index == 0) {
                homePage()
            } else {
                val pageIdx = index - 1
                val pagePkgs = layout.pages.getOrNull(pageIdx).orEmpty()
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        for (r in 0 until grid.rows) {
                            Row(Modifier.fillMaxWidth().weight(1f)) {
                                for (c in 0 until grid.cols) {
                                    val pkg = pagePkgs.getOrNull(r * grid.cols + c)
                                    val app = pkg?.let { appsByPkg[it] }
                                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        if (app != null) {
                                            ClassicCell(
                                                app = app,
                                                prefs = prefs,
                                                pinned = app.packageName in dockPackages,
                                                editing = editing,
                                                isSelected = sel == app.packageName,
                                                onTap = {
                                                    if (!editing) onLaunch(app)
                                                    else if (sel == null) selected = app.packageName
                                                    else if (sel == app.packageName) selected = null
                                                    else place(movePlaced(layout, sel, pageIdx, app.packageName, grid.perPage))
                                                },
                                                onToggleDock = onToggleDock,
                                                onHide = onHide,
                                                onEdit = { onEditingChange(true) }
                                            )
                                        } else if (editing && sel != null) {
                                            Box(
                                                Modifier
                                                    .size(prefs.iconSize.dp)
                                                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                                    .clickable { place(movePlaced(layout, sel, pageIdx, null, grid.perPage)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (editing && pagePkgs.isEmpty() && sel == null) {
                        Text(
                            "Empty page. Pick up an app, then tap here.",
                            color = TextMuted, fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        if (editing && sel != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (layout.pages.any { sel in it }) {
                    ToolPill("Remove from page") { place(removeFromPages(layout, sel)) }
                }
                ToolPill("Cancel") { selected = null }
            }
        }

        if (editing && layout.unplaced.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "UNPLACED (${layout.unplaced.size})  tap one, then tap a page spot",
                color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                layout.unplaced.mapNotNull { appsByPkg[it] }.forEach { app ->
                    Box(
                        Modifier
                            .then(
                                if (sel == app.packageName) Modifier.border(2.dp, accent, RoundedCornerShape(16.dp))
                                else Modifier
                            )
                            .padding(2.dp)
                            .clickable { selected = if (sel == app.packageName) null else app.packageName }
                    ) { NovaIcon(app, 44.dp, prefs.iconShape) }
                }
            }
        }

        val rangeText = if (current == 0) "HOME" else {
            val page = layout.pages.getOrNull(current - 1).orEmpty().mapNotNull { appsByPkg[it] }
            if (page.isEmpty()) "EMPTY" else "${letterOf(page.first())} – ${letterOf(page.last())}"
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(rangeText, color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0..layout.pages.size) {
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
private fun ToolPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .glass(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) { Text(label, color = TextPrimary, fontSize = 13.sp) }
}

@Composable
private fun ClassicCell(
    app: AppInfo,
    prefs: NovaPrefs,
    pinned: Boolean,
    editing: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onToggleDock: (AppInfo) -> Unit,
    onHide: (AppInfo) -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var menu by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val iconDp = minOf(prefs.iconSize.dp, maxWidth - 8.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .bounceClick(onLongClick = { if (!editing) menu = true }) { onTap() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.then(
                    if (isSelected) Modifier.border(2.dp, accent, RoundedCornerShape(20.dp)).padding(3.dp)
                    else Modifier
                )
            ) { NovaIcon(app, iconDp, prefs.iconShape) }
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
            DropdownMenuItem(text = { Text("Edit pages") }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; openAppInfo(context, app) })
            DropdownMenuItem(text = { Text("Hide app") }, onClick = { menu = false; onHide(app) })
        }
    }
}
