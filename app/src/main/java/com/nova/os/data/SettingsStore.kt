package com.nova.os.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val iconShape: String = "squircle",
    val launchLog: List<Pair<String, Long>> = emptyList(),
    val note: String = ""
)

class SettingsStore(private val context: Context) {
    private val accentKey = stringPreferencesKey("accent")
    private val use24hKey = booleanPreferencesKey("use24h")
    private val dockKey = stringPreferencesKey("dock")
    private val wallpaperKey = stringPreferencesKey("wallpaper")
    private val iconShapeKey = stringPreferencesKey("icon_shape")
    private val logKey = stringPreferencesKey("launch_log")
    private val noteKey = stringPreferencesKey("note")

    val flow: Flow<NovaPrefs> = context.novaStore.data.map { p ->
        NovaPrefs(
            accent = p[accentKey] ?: "cyan",
            use24h = p[use24hKey] ?: true,
            dock = p[dockKey]?.split(",")?.filter { it.isNotBlank() },
            wallpaper = p[wallpaperKey] ?: "aurora",
            iconShape = p[iconShapeKey] ?: "squircle",
            launchLog = parseLog(p[logKey]),
            note = p[noteKey] ?: ""
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

    suspend fun logLaunch(packageName: String) = context.novaStore.edit { p ->
        val current = p[logKey]?.lines()?.filter { it.isNotBlank() }.orEmpty()
        val next = (current + "$packageName|${System.currentTimeMillis()}").takeLast(300)
        p[logKey] = next.joinToString("\n")
    }
}
