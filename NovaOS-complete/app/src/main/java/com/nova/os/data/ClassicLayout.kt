package com.nova.os.data

/** Grid used by classic-mode app pages. */
data class GridSpec(val cols: Int, val rows: Int) {
    val perPage: Int get() = cols * rows
}

fun gridSpec(preset: String): GridSpec = when (preset) {
    "4x6" -> GridSpec(4, 6)
    "5x6" -> GridSpec(5, 6)
    "4x5" -> GridSpec(4, 5)
    else -> GridSpec(5, 6)
}

/** App pages (the fixed clock/widgets page is not part of this) plus apps not placed on any page. */
data class ClassicLayout(val pages: List<List<String>>, val unplaced: List<String>)

fun encodePages(pages: List<List<String>>): String =
    pages.joinToString(";") { if (it.isEmpty()) "~" else it.joinToString(",") }

fun decodePages(raw: String?): List<List<String>> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(";").map { seg ->
        if (seg == "~" || seg.isBlank()) emptyList() else seg.split(",").filter { it.isNotBlank() }
    }
}

/**
 * Brings the saved layout in line with the apps that exist: drops removed or hidden apps,
 * splits pages that no longer fit the grid, and appends newly installed apps at the end.
 */
fun reconcile(
    pagesIn: List<List<String>>,
    unplacedIn: List<String>,
    apps: List<AppInfo>,
    perPage: Int
): ClassicLayout {
    if (pagesIn.isEmpty() && unplacedIn.isEmpty()) {
        return ClassicLayout(apps.map { it.packageName }.chunked(perPage), emptyList())
    }
    val valid = apps.map { it.packageName }.toSet()
    val seen = HashSet<String>()
    var pages = pagesIn.map { page -> page.filter { it in valid && seen.add(it) } }
    val unplaced = unplacedIn.filter { it in valid && seen.add(it) }
    pages = pages.flatMap { p -> if (p.size > perPage) p.chunked(perPage) else listOf(p) }

    val fresh = apps.map { it.packageName }.filter { it !in seen }
    if (fresh.isNotEmpty()) {
        val grown = pages.map { it.toMutableList() }.toMutableList()
        if (grown.isEmpty()) grown.add(mutableListOf())
        for (pkg in fresh) {
            if (grown.last().size >= perPage) grown.add(mutableListOf())
            grown.last().add(pkg)
        }
        pages = grown.map { it.toList() }
    }
    return ClassicLayout(pages, unplaced)
}

/** Moves an app onto a page, before [beforePkg] if given, otherwise to the end. Null if the page is full. */
fun movePlaced(layout: ClassicLayout, pkg: String, toPage: Int, beforePkg: String?, perPage: Int): ClassicLayout? {
    if (toPage !in layout.pages.indices) return null
    val without = layout.pages.map { page -> page.filter { it != pkg } }
    val target = without[toPage].toMutableList()
    if (target.size >= perPage) return null
    val at = beforePkg?.let { target.indexOf(it) }?.takeIf { it >= 0 } ?: target.size
    target.add(at, pkg)
    val pages = without.mapIndexed { i, page -> if (i == toPage) target.toList() else page }
    return ClassicLayout(pages, layout.unplaced.filter { it != pkg })
}

fun removeFromPages(layout: ClassicLayout, pkg: String): ClassicLayout =
    ClassicLayout(
        layout.pages.map { page -> page.filter { it != pkg } },
        layout.unplaced.filter { it != pkg } + pkg
    )

fun addPage(layout: ClassicLayout): ClassicLayout =
    ClassicLayout(layout.pages + listOf(emptyList()), layout.unplaced)

/** Deleting a page never deletes apps: they go to the unplaced tray and stay in the A–Z drawer. */
fun deletePage(layout: ClassicLayout, index: Int): ClassicLayout {
    if (index !in layout.pages.indices) return layout
    return ClassicLayout(
        layout.pages.filterIndexed { i, _ -> i != index },
        layout.unplaced + layout.pages[index]
    )
}

private val categoryPageOrder = listOf("Finance", "Social", "Productivity", "Media", "Games", "Tools", "Other")

/** Rebuilds every page, either alphabetically or grouped by category (each category starts a new page). */
fun organize(apps: List<AppInfo>, byCategory: Boolean, perPage: Int): ClassicLayout {
    val sorted = apps.sortedBy { it.label.lowercase() }
    if (!byCategory) return ClassicLayout(sorted.map { it.packageName }.chunked(perPage), emptyList())
    val pages = mutableListOf<List<String>>()
    for (c in categoryPageOrder) {
        val list = sorted.filter { it.category == c }.map { it.packageName }
        if (list.isNotEmpty()) pages.addAll(list.chunked(perPage))
    }
    return ClassicLayout(pages, emptyList())
}
