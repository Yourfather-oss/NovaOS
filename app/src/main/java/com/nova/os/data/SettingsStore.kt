package com.nova.os.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.novaStore by preferencesDataStore(name = "nova_prefs")

data class NovaPrefs(
    val accent: String = "cyan",
    val use24h: Boolean = true,
    val dock: List<String>? = null,
    val wallpaper: String = "aurora",
    val iconShape: String = "original",
    val launchLog: List<Pair<String, Long>> = emptyList(),
    val note: String = "",
    val surface: String = "glass",
    val iconSize: Int = 44,
    val showLabels: Boolean = true,
    val clockWeight: String = "thin",
    val clockFont: String = "default",
    val clockSize: String = "m",
    val animSpeed: String = "normal",
    val liveWallpaper: Boolean = true,
    val gestureUp: String = "drawer",
    val gestureDown: String = "control",
    val gestureLeft: String = "dashboard",
    val gestureRight: String = "widgets",
    val gestureLong: String = "studio",
    val gestureDouble: String = "none",
    val hidden: List<String> = emptyList(),
    val homeMode: String = "nova",
    val classicPages: List<List<String>> = emptyList(),
    val classicUnplaced: List<String> = emptyList(),
    val gridPreset: String = "4x6",
    val dockStyle: String = "floating",
    val iconFinish: String = "plain",
    val tasks: List<Task> = emptyList(),
    val focusLog: List<Long> = emptyList(),
    val aiKey: String = "", // legacy Claude key field; NOVA Intelligence no longer reads or sends it
    val aiEndpoint: String = "",
    val aiModel: String = MODEL_BALANCED,
    val wallpaperDim: Int = 35,
    val wallpaperRev: Long = 0L,
    val weather: String = "",
    val homeWeather: Boolean = true,
    val homeStats: Boolean = true,
    val homeUpcoming: Boolean = true,
    val homeMusic: Boolean = true,
    val homeFrequent: Boolean = true,
    val homeNote: Boolean = false,
    val homeOrder: String = "stats,upcoming,music,frequent,note",
    val frequentPos: String = "bottom"
)

class SettingsStore(private val context: Context) {
    private val accentKey = stringPreferencesKey("accent")
    private val use24hKey = booleanPreferencesKey("use24h")
    private val dockKey = stringPreferencesKey("dock")
    private val wallpaperKey = stringPreferencesKey("wallpaper")
    private val iconShapeKey = stringPreferencesKey("icon_shape")
    private val logKey = stringPreferencesKey("launch_log")
    private val noteKey = stringPreferencesKey("note")
    private val surfaceKey = stringPreferencesKey("surface")
    private val iconSizeKey = intPreferencesKey("icon_size")
    private val showLabelsKey = booleanPreferencesKey("show_labels")
    private val clockWeightKey = stringPreferencesKey("clock_weight")
    private val clockFontKey = stringPreferencesKey("clock_font")
    private val clockSizeKey = stringPreferencesKey("clock_size")
    private val animSpeedKey = stringPreferencesKey("anim_speed")
    private val liveWallpaperKey = booleanPreferencesKey("live_wallpaper")

    val flow: Flow<NovaPrefs> = context.novaStore.data.map { p ->
        NovaPrefs(
            accent = p[accentKey] ?: "cyan",
            use24h = p[use24hKey] ?: true,
            dock = p[dockKey]?.split(",")?.filter { it.isNotBlank() },
            wallpaper = p[wallpaperKey] ?: "aurora",
            iconShape = p[iconShapeKey] ?: "original",
            launchLog = parseLog(p[logKey]),
            note = p[noteKey] ?: "",
            surface = p[surfaceKey] ?: "glass",
            iconSize = p[iconSizeKey] ?: 44,
            showLabels = p[showLabelsKey] ?: true,
            clockWeight = p[clockWeightKey] ?: "thin",
            clockFont = p[clockFontKey] ?: "default",
            clockSize = p[clockSizeKey] ?: "m",
            animSpeed = p[animSpeedKey] ?: "normal",
            liveWallpaper = p[liveWallpaperKey] ?: true,
            gestureUp = p[stringPreferencesKey("g_up")] ?: "drawer",
            gestureDown = p[stringPreferencesKey("g_down")] ?: "control",
            gestureLeft = p[stringPreferencesKey("g_left")] ?: "dashboard",
            gestureRight = p[stringPreferencesKey("g_right")] ?: "widgets",
            gestureLong = p[stringPreferencesKey("g_long")] ?: "studio",
            gestureDouble = p[stringPreferencesKey("g_double")] ?: "none",
            hidden = (p[stringPreferencesKey("hidden")] ?: "").split(",").filter { it.isNotBlank() },
            homeMode = p[stringPreferencesKey("home_mode")] ?: "nova",
            classicPages = decodePages(p[stringPreferencesKey("classic_pages")]),
            classicUnplaced = (p[stringPreferencesKey("classic_unplaced")] ?: "").split(",").filter { it.isNotBlank() },
            gridPreset = p[stringPreferencesKey("grid_preset")] ?: "4x6",
            dockStyle = p[stringPreferencesKey("dock_style")] ?: "floating",
            iconFinish = p[stringPreferencesKey("icon_finish")] ?: "plain",
            tasks = decodeTasks(p[stringPreferencesKey("tasks")]),
            focusLog = (p[stringPreferencesKey("focus_log")] ?: "").split(",").mapNotNull { it.toLongOrNull() },
            aiKey = "",
            aiEndpoint = p[stringPreferencesKey("ai_endpoint")] ?: "",
            aiModel = p[stringPreferencesKey("ai_model")] ?: MODEL_BALANCED,
            wallpaperDim = p[intPreferencesKey("wallpaper_dim")] ?: 35,
            wallpaperRev = p[stringPreferencesKey("wallpaper_rev")]?.toLongOrNull() ?: 0L,
            weather = p[stringPreferencesKey("weather")] ?: "",
            homeWeather = p[booleanPreferencesKey("w_weather")] ?: true,
            homeStats = p[booleanPreferencesKey("w_stats")] ?: true,
            homeUpcoming = p[booleanPreferencesKey("w_upcoming")] ?: true,
            homeMusic = p[booleanPreferencesKey("w_music")] ?: true,
            homeFrequent = p[booleanPreferencesKey("w_frequent")] ?: true,
            homeNote = p[booleanPreferencesKey("w_note")] ?: false,
            homeOrder = p[stringPreferencesKey("home_order")] ?: "stats,upcoming,music,frequent,note",
            frequentPos = p[stringPreferencesKey("frequent_pos")] ?: "bottom"
        )
    }

    private fun parseLog(raw: String?): List<Pair<String, Long>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val i = line.lastIndexOf('|')
            if (i < 0) return@mapNotNull null
            val ts = line.substring(i + 1).toLongOrNull() ?: return@mapNotNull null
            line.substring(0, i) to ts
        }
    }

    suspend fun setAccent(value: String) = context.novaStore.edit { it[accentKey] = value }
    suspend fun setUse24h(value: Boolean) = context.novaStore.edit { it[use24hKey] = value }
    suspend fun setDock(value: List<String>) = context.novaStore.edit { it[dockKey] = value.joinToString(",") }
    suspend fun setWallpaper(value: String) = context.novaStore.edit { it[wallpaperKey] = value }
    suspend fun setIconShape(value: String) = context.novaStore.edit { it[iconShapeKey] = value }
    suspend fun setNote(value: String) = context.novaStore.edit { it[noteKey] = value }
    suspend fun setSurface(value: String) = context.novaStore.edit { it[surfaceKey] = value }
    suspend fun setIconSize(value: Int) = context.novaStore.edit { it[iconSizeKey] = value }
    suspend fun setShowLabels(value: Boolean) = context.novaStore.edit { it[showLabelsKey] = value }
    suspend fun setClockWeight(value: String) = context.novaStore.edit { it[clockWeightKey] = value }
    suspend fun setClockFont(value: String) = context.novaStore.edit { it[clockFontKey] = value }
    suspend fun setClockSize(value: String) = context.novaStore.edit { it[clockSizeKey] = value }
    suspend fun setAnimSpeed(value: String) = context.novaStore.edit { it[animSpeedKey] = value }
    suspend fun setLiveWallpaper(value: Boolean) = context.novaStore.edit { it[liveWallpaperKey] = value }

    suspend fun setWallpaperDim(value: Int) = context.novaStore.edit { it[intPreferencesKey("wallpaper_dim")] = value }

    suspend fun applyTheme(t: ThemePreset) = context.novaStore.edit {
        it[accentKey] = t.accent
        it[wallpaperKey] = t.wallpaper
        it[surfaceKey] = t.surface
        it[iconShapeKey] = t.iconShape
        it[stringPreferencesKey("icon_finish")] = t.iconFinish
        it[clockFontKey] = t.clockFont
        it[clockWeightKey] = t.clockWeight
    }

    suspend fun put(key: String, value: Boolean) = context.novaStore.edit { it[booleanPreferencesKey(key)] = value }

    suspend fun put(key: String, value: String) = context.novaStore.edit { it[stringPreferencesKey(key)] = value }

    suspend fun setClassicLayout(pages: List<List<String>>, unplaced: List<String>) = context.novaStore.edit {
        it[stringPreferencesKey("classic_pages")] = encodePages(pages)
        it[stringPreferencesKey("classic_unplaced")] = unplaced.joinToString(",")
    }

    /**
     * One-time restoration of the original NOVA visual identity.
     * Keep the original four-column / six-row pages, native app artwork and
     * four-icon dock. Existing dock entries are retained and completed from
     * the original defaults rather than replacing the user's current choices.
     */
    suspend fun restoreOriginalVisuals(defaultDock: List<String>) = context.novaStore.edit { prefs ->
        val migrated = booleanPreferencesKey("original_visuals_restored_v1")
        if (prefs[migrated] != true) {
            prefs[stringPreferencesKey("grid_preset")] = "4x6"
            prefs[stringPreferencesKey("home_mode")] = "classic"
            prefs[iconShapeKey] = "original"
            prefs[stringPreferencesKey("icon_finish")] = "plain"
            prefs[iconSizeKey] = 44
            prefs[booleanPreferencesKey("w_stats")] = true
            prefs[booleanPreferencesKey("w_music")] = true
            prefs[booleanPreferencesKey("w_note")] = false
            prefs[stringPreferencesKey("home_order")] = "stats,upcoming,music,frequent,note"

            val existing = prefs[dockKey]?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            val restoredDock = (existing + defaultDock.filterNot { it in existing }).take(4)
            if (restoredDock.size >= 4) prefs[dockKey] = restoredDock.joinToString(",")
            else if (existing.isEmpty()) prefs[dockKey] = defaultDock.take(4).joinToString(",")

            prefs[migrated] = true
        }
    }

    suspend fun setTasks(tasks: List<Task>) = context.novaStore.edit {
        it[stringPreferencesKey("tasks")] = encodeTasks(tasks)
    }

    suspend fun logFocus() = context.novaStore.edit { p ->
        val key = stringPreferencesKey("focus_log")
        val current = (p[key] ?: "").split(",").filter { it.isNotBlank() }
        p[key] = (current + System.currentTimeMillis().toString()).takeLast(200).joinToString(",")
    }

    suspend fun logLaunch(packageName: String) = context.novaStore.edit { p ->
        val current = p[logKey]?.lines()?.filter { it.isNotBlank() }.orEmpty()
        val next = (current + "$packageName|${System.currentTimeMillis()}").takeLast(300)
        p[logKey] = next.joinToString("\n")
    }
}
