package com.nova.os.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: ImageBitmap
)

/** Discovers every launchable app using Android's normal package visibility rules. */
fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(query, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            val ai = it.activityInfo
            AppInfo(
                label = it.loadLabel(pm).toString(),
                packageName = ai.packageName,
                component = ComponentName(ai.packageName, ai.name),
                icon = it.loadIcon(pm).toBitmap(144, 144).asImageBitmap()
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
    val wanted = listOf("dialer", "messag", "chrome", "camera")
    return wanted.mapNotNull { key -> apps.firstOrNull { it.packageName.contains(key, true) }?.packageName }
}
