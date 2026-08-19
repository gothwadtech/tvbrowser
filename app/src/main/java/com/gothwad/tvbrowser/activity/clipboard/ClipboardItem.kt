package com.gothwad.tvbrowser.activity.clipboard

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ClipboardItem(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    var title: String = "",
    var type: String = TYPE_TEXT,
    var timestamp: Long = System.currentTimeMillis(),
    var copyCount: Int = 1,
    var colorHex: String = "#38BDF8"
) {
    val displayTitle: String
        get() {
            if (title.isNotBlank()) return title
            val trimmed = text.trim()
            return if (trimmed.length > 60) {
                trimmed.take(57) + "..."
            } else {
                trimmed.ifEmpty { "Empty item" }
            }
        }

    val previewText: String
        get() {
            val singleLine = text.replace("\n", " ").trim()
            return if (singleLine.length > 100) {
                singleLine.take(97) + "..."
            } else {
                singleLine
            }
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val charCountText: String
        get() = "${text.length} chars"

    val isUrl: Boolean
        get() = text.trim().startsWith("http://", true) ||
                text.trim().startsWith("https://", true) ||
                android.util.Patterns.WEB_URL.matcher(text.trim()).matches()

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("text", text)
            put("title", title)
            put("type", type)
            put("timestamp", timestamp)
            put("copyCount", copyCount)
            put("colorHex", colorHex)
        }
    }

    companion object {
        const val TYPE_TEXT = "TEXT"
        const val TYPE_URL = "LINK"
        const val TYPE_IMAGE = "IMAGE"
        const val TYPE_EMAIL = "EMAIL"
        const val TYPE_CODE = "CODE"

        fun inferType(text: String): String {
            val trimmed = text.trim()
            return when {
                trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) ||
                        android.util.Patterns.WEB_URL.matcher(trimmed).matches() -> TYPE_URL
                trimmed.startsWith("data:image", true) || trimmed.endsWith(".jpg", true) ||
                        trimmed.endsWith(".png", true) || trimmed.endsWith(".webp", true) ||
                        trimmed.endsWith(".gif", true) -> TYPE_IMAGE
                android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches() -> TYPE_EMAIL
                trimmed.startsWith("{") || trimmed.startsWith("<") || trimmed.contains("function") || trimmed.contains("const ") -> TYPE_CODE
                else -> TYPE_TEXT
            }
        }

        fun fromJsonObject(obj: JSONObject): ClipboardItem {
            return ClipboardItem(
                id = obj.optString("id", UUID.randomUUID().toString()),
                text = obj.optString("text", ""),
                title = obj.optString("title", ""),
                type = obj.optString("type", TYPE_TEXT),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                copyCount = obj.optInt("copyCount", 1),
                colorHex = obj.optString("colorHex", "#38BDF8")
            )
        }
    }
}
