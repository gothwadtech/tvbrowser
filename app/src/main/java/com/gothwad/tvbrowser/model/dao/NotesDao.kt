package com.gothwad.tvbrowser.model.dao

import androidx.room.*
import com.gothwad.tvbrowser.notes.NoteItem
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, timestamp DESC")
    suspend fun getAllNotes(): List<NoteItem>

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, timestamp DESC")
    fun getAllNotesFlow(): Flow<List<NoteItem>>

    @Query("SELECT * FROM notes WHERE isArchived = 0 ORDER BY isPinned DESC, timestamp DESC")
    suspend fun getActiveNotes(): List<NoteItem>

    @Query("SELECT * FROM notes WHERE isArchived = 1 ORDER BY isPinned DESC, timestamp DESC")
    suspend fun getArchivedNotes(): List<NoteItem>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteItem>)

    @Update
    suspend fun update(note: NoteItem)

    @Delete
    suspend fun delete(note: NoteItem)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE notes SET isArchived = :isArchived WHERE id IN (:ids)")
    suspend fun updateArchivedStatus(ids: List<String>, isArchived: Boolean)

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun getCount(): Int

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
