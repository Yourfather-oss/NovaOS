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

data class AiMessage(val role: String, val text: String)
data class ProposedTask(val title: String, val whenText: String?)
data class AiReply(val reply: String, val tasks: List<ProposedTask>)

private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

const val MODEL_FAST = "claude-haiku-5-5"
const val MODEL_SMART = "claude-sonnet-5-5"

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
        "You are NOVA, the personal assistant built into the user's Android launcher.",
        "Be concise, practical and warm. Never invent commitments the user did not mention.",
        "Current local date and time: ${now.format(stamp)} (${now.dayOfWeek}).",
        "Focus sessions completed today: $focusToday.",
        "",
        "Open tasks:",
        open,
        "",
        "Recently completed:",
        done,
        "",
        "Reply with ONLY a JSON object, with no markdown, in exactly this shape:",
        "{\"reply\": \"your message to the user\", \"tasks\": [{\"title\": \"short task title\", \"when\": \"yyyy-MM-dd HH:mm or null\"}]}",
        "Put in \"tasks\" only NEW tasks the user asked you to add or plan, otherwise use an empty list.",
        "Resolve words like tomorrow from the current date. Space tasks realistically and avoid clashing with open tasks.",
        "The user approves tasks before they are added, so never claim they were already added."
    ).joinToString("\n")
}

/** Calls the Anthropic Messages API directly from the phone with the user's own key. */
suspend fun askClaude(
    apiKey: String,
    model: String,
    system: String,
    messages: List<AiMessage>
): Result<String> = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        val msgs = JSONArray()
        messages.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.text)) }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 2048)
            .put("system", system)
            .put("messages", msgs)

        conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (code !in 200..299) {
            val apiMessage = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
            val msg = when (code) {
                401 -> "That API key was rejected. Check it in the Ask NOVA settings."
                429 -> "Rate limit reached. Try again shortly."
                else -> apiMessage?.takeIf { it.isNotBlank() } ?: "Request failed ($code)."
            }
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val content = JSONObject(text).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") sb.append(block.optString("text"))
        }
        Result.success(sb.toString())
    } catch (e: IOException) {
        Result.failure(IllegalStateException("Couldn't reach Anthropic. Check your internet connection."))
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

/** Parses "yyyy-MM-dd HH:mm" in local time. */
fun parseWhen(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    return runCatching {
        LocalDateTime.parse(text.trim().replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}
