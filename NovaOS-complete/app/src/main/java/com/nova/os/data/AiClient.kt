package com.nova.os.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Chat messages exchanged with NOVA Intelligence. */
data class AiMessage(val role: String, val text: String)
data class ProposedTask(val title: String, val whenText: String?)
data class AiReply(val reply: String, val tasks: List<ProposedTask>)

private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** Balanced model is configurable on the managed backend; this is its default. */
const val MODEL_BALANCED = "gpt-6.2-sol"

fun buildSystemPrompt(now: LocalDateTime, tasks: List<Task>, focusToday: Int): String {
    val zone = ZoneId.systemDefault()
    fun fmt(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().format(stamp)
    val open = tasks.filter { !it.done }.take(25)
        .joinToString("\n") { t -> "- " + t.title + (t.due?.let { " (due ${fmt(it)})" } ?: "") }
        .ifBlank { "none" }
    val done = tasks.filter { it.done }.takeLast(5)
        .joinToString("\n") { "- " + it.title }
        .ifBlank { "none" }
    return listOf(
        "You are NOVA Intelligence, the personal assistant built into the user's Android launcher.",
        "Be concise, practical, warm and proactive without being pushy. Never invent commitments, calendar events or completed actions.",
        "Current local date and time: ${now.format(stamp)} (${now.dayOfWeek}).",
        "Focus sessions completed today: $focusToday.",
        "Open tasks:", open,
        "Recently completed:", done,
        "Return ONLY valid JSON in this shape: {\"reply\":\"message\",\"tasks\":[{\"title\":\"task\",\"when\":\"yyyy-MM-dd HH:mm or null\"}]}",
        "Only include new tasks the user explicitly asked you to create or clearly requested as part of a plan. Otherwise return an empty tasks array.",
        "Resolve relative dates using the supplied local date and time. Do not claim a task or reminder has been saved: the user must approve proposed tasks first.",
        "You cannot read the phone calendar or operate Android apps unless the app supplies that information or a future approved device action. Be honest about this limitation."
    ).joinToString("\n")
}

/** Sends requests to the user's managed NOVA backend. The OpenAI key never lives in the APK. */
suspend fun askNOVA(
    endpoint: String,
    model: String,
    system: String,
    messages: List<AiMessage>
): Result<String> = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        require(endpoint.startsWith("https://")) { "Use your secure HTTPS NOVA backend URL." }
        val history = JSONArray()
        messages.takeLast(12).forEach { history.put(JSONObject().put("role", it.role).put("content", it.text)) }
        val body = JSONObject()
            .put("model", model.ifBlank { MODEL_BALANCED })
            .put("instructions", system)
            .put("messages", history)

        conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val payload = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(payload).optString("error") }.getOrNull().orEmpty()
            val message = when (code) {
                401, 403 -> "NOVA backend authentication failed. Check its deployment settings."
                429 -> "NOVA is receiving too many requests. Try again shortly."
                502, 503 -> "The GPT service is temporarily unavailable. Try again shortly."
                else -> detail.ifBlank { "NOVA backend request failed ($code)." }
            }
            return@withContext Result.failure(IllegalStateException(message))
        }
        val result = JSONObject(payload)
        val answer = result.optString("text").ifBlank { result.optString("output_text") }
        if (answer.isBlank()) Result.failure(IllegalStateException("The NOVA backend returned an empty reply."))
        else Result.success(answer)
    } catch (e: IOException) {
        Result.failure(IllegalStateException("Couldn't reach your NOVA backend. Check the URL and internet connection."))
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        conn?.disconnect()
    }
}

fun parseAiReply(raw: String): AiReply {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start >= 0 && end > start) {
        val parsed = runCatching {
            val o = JSONObject(raw.substring(start, end + 1))
            val arr = o.optJSONArray("tasks") ?: JSONArray()
            val tasks = (0 until arr.length()).mapNotNull { i ->
                val t = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = t.optString("title").trim()
                if (title.isEmpty()) null
                else ProposedTask(title, t.optString("when").takeIf { it.isNotBlank() && it != "null" })
            }
            AiReply(o.optString("reply").ifBlank { raw.trim() }, tasks)
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return AiReply(raw.trim(), emptyList())
}

/** Parses yyyy-MM-dd HH:mm in the device's local time zone. */
fun parseWhen(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    return runCatching {
        LocalDateTime.parse(text.trim().replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}
