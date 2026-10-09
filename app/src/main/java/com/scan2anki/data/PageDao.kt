package com.scan2anki.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE sessionId = :sessionId ORDER BY \"order\"")
    fun observeBySession(sessionId: Long): Flow<List<Page>>

    @Insert
    suspend fun insert(page: Page): Long

    @Update
    suspend fun update(page: Page)

    @Query("UPDATE pages SET ocrState = :state WHERE id = :pageId")
    suspend fun updateOcrState(pageId: Long, state: String)

    @Query("UPDATE pages SET zoneCacheJson = :json WHERE id = :pageId")
    suspend fun updateZoneCache(pageId: Long, json: String)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deleteById(pageId: Long)
}
