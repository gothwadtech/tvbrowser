package com.gothwad.tvbrowser.notes.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.gothwad.tvbrowser.model.dao.ClipboardDao
import com.gothwad.tvbrowser.singleton.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ClipboardRepository(
    private val context: Context,
    private val clipboardDao: ClipboardDao = AppDatabase.db.clipboardDao()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tv_clipboard_history_v2", Context.MODE_PRIVATE)
    private val KEY_ITEMS = "saved_clipboard_items_json"
    private val KEY_MIGRATED_ROOM = "migrated_to_room_v1"

    suspend fun checkAndMigrateFromPrefs() = withContext(Dispatchers.IO) {
        if (!prefs.getBoolean(KEY_MIGRATED_ROOM, false)) {
            val jsonStr = prefs.getString(KEY_ITEMS, null)
            if (!jsonStr.isNullOrEmpty()) {
                try {
                    val array = JSONArray(jsonStr)
                    val list = mutableListOf<ClipboardItem>()
                    for (i in 0 until array.length()) {
                        list.add(ClipboardItem.fromJsonObject(array.getJSONObject(i)))
                    }
                    if (list.isNotEmpty()) {
                        clipboardDao.insertAll(list)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            prefs.edit().putBoolean(KEY_MIGRATED_ROOM, true).apply()
        }
    }

    suspend fun getAllItems(): List<ClipboardItem> = withContext(Dispatchers.IO) {
        checkAndMigrateFromPrefs()
        clipboardDao.getRecent(250)
    }

    suspend fun recordCopiedText(rawText: String, customTitle: String = ""): ClipboardItem? = withContext(Dispatchers.IO) {
        val text = rawText.trim()
        if (text.isEmpty()) {
            return@withContext null
        }
        checkAndMigrateFromPrefs()

        val existing = clipboardDao.findByText(text)
        val item: ClipboardItem
        if (existing != null) {
            existing.timestamp = System.currentTimeMillis()
            existing.copyCount += 1
            if (customTitle.isNotBlank()) existing.title = customTitle
            clipboardDao.update(existing)
            item = existing
        } else {
            val inferredType = ClipboardItem.inferType(text)
            item = ClipboardItem(
                text = text,
                title = customTitle,
                type = inferredType,
                timestamp = System.currentTimeMillis(),
                copyCount = 1,
                colorHex = when (inferredType) {
                    ClipboardItem.TYPE_URL -> "#38BDF8"
                    ClipboardItem.TYPE_IMAGE -> "#F472B6"
                    ClipboardItem.TYPE_EMAIL -> "#C084FC"
                    ClipboardItem.TYPE_CODE -> "#34D399"
                    else -> "#38BDF8"
                }
            )
            clipboardDao.insert(item)
        }

        clipboardDao.trimToLimit(250)
        item
    }

    suspend fun deleteItem(itemId: String) = withContext(Dispatchers.IO) {
        clipboardDao.deleteById(itemId)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        clipboardDao.deleteAll()
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    suspend fun copyToSystemClipboard(item: ClipboardItem, showToast: Boolean = true) = withContext(Dispatchers.Main) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(item.displayTitle, item.text)

            markCopiedByApp(item.text)
            isInternalClipboardWrite = true
            try {
                clipboard.setPrimaryClip(clip)
            } finally {
                isInternalClipboardWrite = false
            }

            withContext(Dispatchers.IO) {
                item.copyCount += 1
                item.timestamp = System.currentTimeMillis()
                clipboardDao.update(item)
            }

            if (showToast) {
                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (showToast) {
                Toast.makeText(context, "Failed to copy", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        @Volatile
        var isInternalClipboardWrite: Boolean = false

        @Volatile
        var lastCopiedByAppText: String? = null

        @Volatile
        var lastCopiedByAppTime: Long = 0L

        fun markCopiedByApp(text: String) {
            lastCopiedByAppText = text.trim()
            lastCopiedByAppTime = android.os.SystemClock.uptimeMillis()
        }
    }
}
