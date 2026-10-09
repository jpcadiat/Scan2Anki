package com.scan2anki.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportSessionDao {
    @Query("SELECT * FROM import_sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImportSession>>

    @Query("SELECT * FROM import_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<ImportSession?>

    @Insert
    suspend fun insert(session: ImportSession): Long

    @Update
    suspend fun update(session: ImportSession)

    @Query("DELETE FROM import_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
