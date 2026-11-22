package com.nova.os.data

import android.content.Context
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Local-only WhatsApp message cache. No content is uploaded until the user taps Analyze. */
data class NovaMessage(
    val id: String,
    val source: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val imported: Boolean = false
)

object MessageIntelligenceStore {
    private const val PREFS = "nova_message_intelligence"
    private const val KEY_ENABLED = "capture_enabled"
    private const val KEY_MESSAGES = "messages"
    private const val MAX_MESSAGES = 300
    private const val WHATSAPP = "com.whatsapp"
    private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isWhatsApp(packageName: String): Boolean = packageName == WHATSAPP || packageName == WHATSAPP_BUSINESS

    fun capture(context: Context, sbn: StatusBarNotification) {
        if (!isEnabled(context) || !isWhatsApp(sbn.packageName)) return
        val extras = sbn.notification.extras ?: return
        val sender = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val text = buildString {
            val big = extras.getCharSequence("android.bigText")?.toString()
            val plain = extras.getCharSequence("android.text")?.toString()
            append((big ?: plain).orEmpty().trim())
            val lines = extras.getCharSequenceArray("android.textLines")
            if (isEmpty() && lines != null) append(lines.joinToString("\n") { it.toString() })
        }.trim()
        if (text.isBlank() || text == "This message was deleted") return
        save(context, NovaMessage(hash("${sbn.key}|${sbn.postTime}|$sender|$text"), "WhatsApp", sender, text, sbn.postTime))
    }

    fun importExport(context: Context, exportedText: String) {
        val clean = exportedText.trim()
        if (clean.isBlank()) return
        // Keep the original export as one locally stored record; GPT can parse its timestamps/senders.
        save(context, NovaMessage(hash("export|${clean.take(500)}|${System.currentTimeMillis()}"), "WhatsApp export", "Imported chat", clean.take(100_000), System.currentTimeMillis(), true))
    }

    fun getMessages(context: Context): List<NovaMessage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MESSAGES, "[]") ?: "[]"
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                NovaMessage(o.optString("id"), o.optString("source"), o.optString("sender"), o.optString("body"), o.optLong("receivedAt"), o.optBoolean("imported"))
            }.sortedByDescending { it.receivedAt }
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_MESSAGES).apply()
    }

    private fun save(context: Context, message: NovaMessage) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = getMessages(context).toMutableList()
        if (current.any { it.id == message.id }) return
        current.add(0, message)
        val json = JSONArray()
        current.take(MAX_MESSAGES).forEach { m ->
            json.put(JSONObject().put("id", m.id).put("source", m.source).put("sender", m.sender)
                .put("body", m.body).put("receivedAt", m.receivedAt).put("imported", m.imported))
        }
        prefs.edit().putString(KEY_MESSAGES, json.toString()).apply()
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
