package com.nova.os.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

data class CalEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

/** Today's remaining events from the phone's calendars. Needs READ_CALENDAR. */
fun todaysEvents(context: Context): List<CalEvent> = runCatching {
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = start + 24L * 60 * 60 * 1000
    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, start)
    ContentUris.appendId(builder, end)
    val out = mutableListOf<CalEvent>()
    context.contentResolver.query(
        builder.build(),
        arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        ),
        null, null,
        "${CalendarContract.Instances.BEGIN} ASC"
    )?.use { c ->
        while (c.moveToNext()) {
            out += CalEvent(c.getString(0) ?: "Event", c.getLong(1), c.getLong(2), c.getInt(3) == 1)
        }
    }
    val now = System.currentTimeMillis()
    out.filter { it.allDay || it.end >= now }
}.getOrDefault(emptyList())
