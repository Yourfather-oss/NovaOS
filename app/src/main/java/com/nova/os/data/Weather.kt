package com.nova.os.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class WeatherNow(val temp: Int, val code: Int, val isDay: Boolean, val place: String, val at: Long) {
    val label: String
        get() = when (code) {
            0 -> if (isDay) "Clear" else "Clear night"
            1 -> "Mostly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            in 51..57 -> "Drizzle"
            in 61..67 -> "Rain"
            in 71..77 -> "Snow"
            in 80..82 -> "Showers"
            85, 86 -> "Snow showers"
            in 95..99 -> "Thunderstorm"
            else -> "Weather"
        }

    val emoji: String
        get() = when (code) {
            0 -> if (isDay) "☀️" else "🌙"
            1, 2 -> if (isDay) "⛅" else "☁️"
            3 -> "☁️"
            45, 48 -> "🌫️"
            in 51..57, in 80..82 -> "🌦️"
            in 61..67 -> "🌧️"
            in 71..77, 85, 86 -> "🌨️"
            in 95..99 -> "⛈️"
            else -> "🌡️"
        }
}

/** Stored as "temp|code|isDay|place|timestamp". */
fun parseWeather(raw: String): WeatherNow? {
    if (raw.isBlank()) return null
    val parts = raw.split("|")
    if (parts.size < 5) return null
    return runCatching {
        WeatherNow(parts[0].toInt(), parts[1].toInt(), parts[2] == "1", parts[3], parts[4].toLong())
    }.getOrNull()
}

@SuppressLint("MissingPermission")
private fun lastLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
}

@SuppressLint("MissingPermission")
private suspend fun currentLocation(context: Context): Location? {
    lastLocation(context)?.let { return it }
    if (Build.VERSION.SDK_INT < 30) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return withTimeoutOrNull(8000) {
        suspendCancellableCoroutine<Location?> { cont ->
            runCatching {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }
}

/** Needs coarse location permission and internet. Uses Open-Meteo, which needs no API key. */
suspend fun fetchWeatherHere(context: Context): String? = withContext(Dispatchers.IO) {
    val loc = currentLocation(context) ?: return@withContext null
    var conn: HttpURLConnection? = null
    try {
        val lat = String.format(Locale.US, "%.3f", loc.latitude)
        val lon = String.format(Locale.US, "%.3f", loc.longitude)
        conn = (URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day&timezone=auto"
        ).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
        }
        if (conn.responseCode !in 200..299) return@withContext null
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val cur = JSONObject(text).getJSONObject("current")
        val temp = cur.getDouble("temperature_2m").roundToInt()
        val code = cur.getInt("weather_code")
        val day = if (cur.optInt("is_day", 1) == 1) "1" else "0"

        val place = runCatching {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                val a = Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                a?.locality ?: a?.subAdminArea ?: a?.adminArea
            } else null
        }.getOrNull() ?: "Your location"

        "$temp|$code|$day|${place.replace("|", " ")}|${System.currentTimeMillis()}"
    } catch (e: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}
