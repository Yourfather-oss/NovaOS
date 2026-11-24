package com.nova.os.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: ImageBitmap,
    val category: String
)

private fun categorize(label: String, pkg: String, cat: Int): String {
    val key = (label + " " + pkg).lowercase()
    fun has(vararg words: String) = words.any { key.contains(it) }
    return when {
        has("bank", "pesa", "equity", "absa", "binance", "wallet", "finance", "money", "kcb", "stanbic", "paypal", "fuliza") -> "Finance"
        cat == ApplicationInfo.CATEGORY_GAME -> "Games"
        cat == ApplicationInfo.CATEGORY_SOCIAL ||
            has("whatsapp", "telegram", "facebook", "messenger", "instagram", "tiktok", "snapchat", "pinterest", "twitter", "truecaller") ||
            label == "X" -> "Social"
        cat == ApplicationInfo.CATEGORY_AUDIO || cat == ApplicationInfo.CATEGORY_VIDEO || cat == ApplicationInfo.CATEGORY_IMAGE ||
            has("youtube", "spotify", "music", "photos", "gallery", "netflix", "shazam", "snaptube", "cricfy") -> "Media"
        cat == ApplicationInfo.CATEGORY_PRODUCTIVITY ||
            has("word", "excel", "powerpoint", "outlook", "docs", "drive", "notes", "onedrive", "wps", "calendar", "office", "chatgpt", "claude") -> "Productivity"
        has("calculator", "clock", "file", "settings", "compass", "recorder", "scanner", "phone", "contacts", "camera", "cloner", "weather", "tips") -> "Tools"
        else -> "Other"
    }
}

/** Discovers every launchable app using Android's normal package visibility rules. */
fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(query, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            val ai = it.activityInfo
            val label = it.loadLabel(pm).toString()
            AppInfo(
                label = label,
                packageName = ai.packageName,
                component = ComponentName(ai.packageName, ai.name),
                icon = it.loadIcon(pm).toBitmap(144, 144).asImageBitmap(),
                category = categorize(label, ai.packageName, ai.applicationInfo.category)
            )
        }
        .distinctBy { it.component }
        .sortedBy { it.label.lowercase() }
}

fun launchApp(context: Context, app: AppInfo) {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = app.component
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
    runCatching { context.startActivity(intent) }
}

fun openAppInfo(context: Context, app: AppInfo) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** First-run dock: match common apps by package name, in this order. */
fun defaultDock(apps: List<AppInfo>): List<String> {
    // Original NOVA dock: Messages, Chrome, Camera, Truecaller.
    val wanted = listOf("messag", "chrome", "camera", "truecaller")
    return wanted.mapNotNull { key -> apps.firstOrNull { it.packageName.contains(key, true) }?.packageName }
}
