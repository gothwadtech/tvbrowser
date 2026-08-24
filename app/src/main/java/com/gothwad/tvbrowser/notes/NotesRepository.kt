package com.gothwad.tvbrowser.notes

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class NotesRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tv_browser_notes_v1", Context.MODE_PRIVATE)
    private val KEY_NOTES = "saved_notes_json"

    fun getAllNotes(): MutableList<NoteItem> {
        val list = mutableListOf<NoteItem>()
        val jsonStr = prefs.getString(KEY_NOTES, null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(NoteItem.fromJsonObject(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (list.isEmpty()) {
            list.add(
                NoteItem(
                    title = "📌 Welcome to TV Notes",
                    content = "Create, edit and organize your ideas, shopping lists, and website links directly on your TV screen!\n\n• Press '+ New Note' to create\n• Use TV Remote D-Pad to navigate\n• Long-press to color code or delete.",
                    colorHex = "#065F46",
                    isPinned = true
                )
            )
            list.add(
                NoteItem(
                    title = "📺 TV Quick Links & Watchlist",
                    content = "1. JioCinema IPL Live\n2. Hotstar Specials\n3. YouTube Tech Documentaries\n4. Netflix Movies",
                    colorHex = "#1E3A8A",
                    isPinned = false
                )
            )
            saveAllNotes(list)
        }

        list.sortWith(compareByDescending<NoteItem> { it.isPinned }.thenByDescending { it.timestamp })
        return list
    }

    fun saveAllNotes(notes: List<NoteItem>) {
        val array = JSONArray()
        for (note in notes) {
            array.put(note.toJsonObject())
        }
        prefs.edit().putString(KEY_NOTES, array.toString()).apply()
    }

    fun addOrUpdateNote(note: NoteItem) {
        val notes = getAllNotes()
        val index = notes.indexOfFirst { it.id == note.id }
        if (index != -1) {
            notes[index] = note
        } else {
            notes.add(0, note)
        }
        saveAllNotes(notes)
    }

    fun deleteNote(noteId: String) {
        val notes = getAllNotes()
        notes.removeAll { it.id == noteId }
        saveAllNotes(notes)
    }
}
