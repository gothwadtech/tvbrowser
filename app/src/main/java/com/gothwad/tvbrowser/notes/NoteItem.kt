package com.gothwad.tvbrowser.notes

import org.json.JSONObject
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NoteItem(
    val id: String = System.currentTimeMillis().toString(),
    var title: String = "",
    var content: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var colorHex: String = "#1E293B",
    var isPinned: Boolean = false
) : Serializable {

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("content", content)
            put("timestamp", timestamp)
            put("colorHex", colorHex)
            put("isPinned", isPinned)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): NoteItem {
            return NoteItem(
                id = obj.optString("id", System.currentTimeMillis().toString()),
                title = obj.optString("title", ""),
                content = obj.optString("content", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                colorHex = obj.optString("colorHex", "#1E293B"),
                isPinned = obj.optBoolean("isPinned", false)
            )
        }
    }
}
