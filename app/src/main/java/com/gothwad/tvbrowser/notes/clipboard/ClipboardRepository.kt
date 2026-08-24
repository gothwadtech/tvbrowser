package com.gothwad.tvbrowser.notes.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import org.json.JSONArray

class ClipboardRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tv_clipboard_history_v2", Context.MODE_PRIVATE)
    private val KEY_ITEMS = "saved_clipboard_items_json"

    fun getAllItems(): MutableList<ClipboardItem> {
        val list = mutableListOf<ClipboardItem>()
        val jsonStr = prefs.getString(KEY_ITEMS, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(ClipboardItem.fromJsonObject(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Sorted by timestamp descending (newest on top)
        list.sortByDescending { it.timestamp }
        return list
    }

    fun saveAllItems(items: List<ClipboardItem>) {
        val array = JSONArray()
        for (item in items) {
            array.put(item.toJsonObject())
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun recordCopiedText(rawText: String, customTitle: String = ""): ClipboardItem? {
        val text = rawText.trim()
        if (text.isEmpty()) {
            return null
        }

        val allItems = getAllItems()
        val existingIndex = allItems.indexOfFirst { it.text.trim() == text }

        val item: ClipboardItem
        if (existingIndex != -1) {
            item = allItems[existingIndex]
            item.timestamp = System.currentTimeMillis()
            item.copyCount += 1
            if (customTitle.isNotBlank()) item.title = customTitle
            allItems.removeAt(existingIndex)
            allItems.add(0, item)
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
            allItems.add(0, item)
        }

        // Limit to max 250 history items
        val trimmedList = if (allItems.size > 250) allItems.take(250) else allItems
        saveAllItems(trimmedList)
        return item
    }

    fun deleteItem(itemId: String) {
        val allItems = getAllItems()
        allItems.removeAll { it.id == itemId }
        saveAllItems(allItems)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    fun copyToSystemClipboard(item: ClipboardItem, showToast: Boolean = true) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(item.displayTitle, item.text)
            clipboard.setPrimaryClip(clip)

            item.copyCount += 1
            item.timestamp = System.currentTimeMillis()

            val allItems = getAllItems()
            val idx = allItems.indexOfFirst { it.id == item.id }
            if (idx != -1) {
                allItems.removeAt(idx)
                allItems.add(0, item)
                saveAllItems(allItems)
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
}
