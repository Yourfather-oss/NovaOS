package com.nova.os.data

/** A complete look: one tap sets accent, wallpaper, surface, icon design and clock style together. */
data class ThemePreset(
    val id: String,
    val name: String,
    val accent: String,
    val wallpaper: String,
    val surface: String,
    val iconShape: String,
    val iconFinish: String,
    val clockFont: String,
    val clockWeight: String
)

val ThemePresets: List<ThemePreset> = listOf(
    ThemePreset("aurora", "Aurora", "cyan", "aurora", "glass", "squircle", "glass", "default", "thin"),
    ThemePreset("nebula", "Nebula", "violet", "nebula", "clear", "circle", "glass", "default", "light"),
    ThemePreset("gold", "Midnight Gold", "amber", "midnight", "matte", "circle", "tile", "serif", "light"),
    ThemePreset("ember", "Ember", "amber", "ember", "glass", "teardrop", "glass", "default", "regular"),
    ThemePreset("ocean", "Ocean", "blue", "ocean", "glass", "rounded", "glass", "default", "thin"),
    ThemePreset("forest", "Forest", "mint", "forest", "matte", "teardrop", "tile", "serif", "light"),
    ThemePreset("rose", "Rose Quartz", "rose", "rose", "clear", "circle", "mono", "serif", "thin"),
    ThemePreset("neon", "Neon City", "violet", "nebula", "matte", "hexagon", "neon", "mono", "regular"),
    ThemePreset("mono", "Monochrome", "ice", "mono", "matte", "rounded", "mono", "default", "thin")
)
