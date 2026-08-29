package com.gothwad.tvbrowser.notes

import android.content.Context
import android.content.SharedPreferences
import com.gothwad.tvbrowser.model.dao.NotesDao
import com.gothwad.tvbrowser.singleton.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class NotesRepository(
    private val context: Context,
    private val notesDao: NotesDao = AppDatabase.db.notesDao()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tv_browser_notes_v1", Context.MODE_PRIVATE)
    private val KEY_NOTES = "saved_notes_json"
    private val KEY_MIGRATED_ROOM = "migrated_to_room_v1"

    suspend fun checkAndMigrateFromPrefs() = withContext(Dispatchers.IO) {
        if (!prefs.getBoolean(KEY_MIGRATED_ROOM, false)) {
            val jsonStr = prefs.getString(KEY_NOTES, null)
            val list = mutableListOf<NoteItem>()
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

            if (list.isNotEmpty()) {
                notesDao.insertAll(list)
            } else if (notesDao.getCount() == 0) {
                // Default starter notes for new users
                notesDao.insert(
                    NoteItem(
                        title = "📌 Welcome to TV Notes",
                        content = "Create, edit and organize your ideas, shopping lists, and website links directly on your TV screen!\n\n• Press '+ New Note' to create\n• Use TV Remote D-Pad to navigate\n• Long-press or click Select to multi-select, archive or delete.",
                        colorHex = "#065F46",
                        isPinned = true,
                        isArchived = false
                    )
                )
                notesDao.insert(
                    NoteItem(
                        title = "📺 TV Quick Links & Watchlist",
                        content = "1. JioCinema IPL Live\n2. Hotstar Specials\n3. YouTube Tech Documentaries\n4. Netflix Movies",
                        colorHex = "#1E3A8A",
                        isPinned = false,
                        isArchived = false
                    )
                )
            }
            prefs.edit().putBoolean(KEY_MIGRATED_ROOM, true).apply()
        }
    }

    suspend fun getAllNotes(): List<NoteItem> = withContext(Dispatchers.IO) {
        checkAndMigrateFromPrefs()
        notesDao.getAllNotes()
    }

    suspend fun addOrUpdateNote(note: NoteItem) = withContext(Dispatchers.IO) {
        checkAndMigrateFromPrefs()
        notesDao.insert(note)
    }

    suspend fun deleteNote(noteId: String) = withContext(Dispatchers.IO) {
        notesDao.deleteById(noteId)
    }

    suspend fun deleteNotes(noteIds: Set<String>) = withContext(Dispatchers.IO) {
        if (noteIds.isNotEmpty()) {
            notesDao.deleteByIds(noteIds.toList())
        }
    }

    suspend fun archiveNotes(noteIds: Set<String>, archive: Boolean) = withContext(Dispatchers.IO) {
        if (noteIds.isNotEmpty()) {
            notesDao.updateArchivedStatus(noteIds.toList(), archive)
        }
    }
}
