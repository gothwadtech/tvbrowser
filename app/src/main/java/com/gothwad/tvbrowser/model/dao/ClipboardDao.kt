package com.gothwad.tvbrowser.model.dao

import androidx.room.*
import com.gothwad.tvbrowser.notes.clipboard.ClipboardItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<ClipboardItem>

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<ClipboardItem>>

    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 250): List<ClipboardItem>

    @Query("SELECT * FROM clipboard_history WHERE id = :id")
    suspend fun getById(id: String): ClipboardItem?

    @Query("SELECT * FROM clipboard_history WHERE text = :text LIMIT 1")
    suspend fun findByText(text: String): ClipboardItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ClipboardItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ClipboardItem>)

    @Update
    suspend fun update(item: ClipboardItem)

    @Delete
    suspend fun delete(item: ClipboardItem)

    @Query("DELETE FROM clipboard_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM clipboard_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM clipboard_history")
    suspend fun getCount(): Int

    @Query("DELETE FROM clipboard_history WHERE id NOT IN (SELECT id FROM clipboard_history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trimToLimit(limit: Int = 250)
}
