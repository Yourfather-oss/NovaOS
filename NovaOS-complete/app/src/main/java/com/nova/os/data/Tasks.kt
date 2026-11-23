package com.nova.os.data

import org.json.JSONArray
import org.json.JSONObject

data class Task(
    val id: Long,
    val title: String,
    val due: Long?,
    val done: Boolean = false,
    val doneAt: Long? = null
)

fun encodeTasks(tasks: List<Task>): String {
    val arr = JSONArray()
    tasks.forEach { t ->
        arr.put(
            JSONObject()
                .put("id", t.id)
                .put("title", t.title)
                .put("due", t.due ?: JSONObject.NULL)
                .put("done", t.done)
                .put("doneAt", t.doneAt ?: JSONObject.NULL)
        )
    }
    return arr.toString()
}

fun decodeTasks(raw: String?): List<Task> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Task(
                id = o.getLong("id"),
                title = o.getString("title"),
                due = if (o.isNull("due")) null else o.getLong("due"),
                done = o.optBoolean("done", false),
                doneAt = if (o.isNull("doneAt")) null else o.getLong("doneAt")
            )
        }
    }.getOrDefault(emptyList())
}
