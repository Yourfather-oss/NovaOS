package com.nova.os.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

data class SysStats(
    val battery: Int = 0,
    val charging: Boolean = false,
    val storagePct: Int = 0,
    val ramPct: Int = 0
)

fun readStats(context: Context): SysStats {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    val battery = if (level >= 0 && scale > 0) level * 100 / scale else 0

    val sf = StatFs(Environment.getDataDirectory().path)
    val total = sf.totalBytes.toDouble()
    val storagePct = if (total > 0) (((total - sf.availableBytes) / total) * 100).toInt() else 0

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo()
    am.getMemoryInfo(mi)
    val ramPct = if (mi.totalMem > 0) (((mi.totalMem - mi.availMem).toDouble() / mi.totalMem) * 100).toInt() else 0

    return SysStats(battery, charging, storagePct, ramPct)
}
