package com.scan2anki.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordPairDao {
    @Query(
        """
        SELECT word_pairs.* FROM word_pairs
        LEFT JOIN pages ON word_pairs.pageId = pages.id
        WHERE word_pairs.sessionId = :sessionId
        ORDER BY CASE WHEN pages.id IS NULL THEN 1 ELSE 0 END, pages."order", word_pairs."order"
        """,
    )
    fun observeBySession(sessionId: Long): Flow<List<WordPair>>

    @Query("DELETE FROM word_pairs WHERE sessionId = :sessionId AND pageId = :pageId")
    suspend fun deleteByPage(sessionId: Long, pageId: Long)

    @Insert
    suspend fun insert(pair: WordPair): Long

    @Insert
    suspend fun insertAll(pairs: List<WordPair>)

    @Transaction
    suspend fun replaceForPage(sessionId: Long, pageId: Long, rows: List<WordPair>) {
        deleteByPage(sessionId, pageId)
        insertAll(rows)
    }

    @Update
    suspend fun update(pair: WordPair)

    @Delete
    suspend fun delete(pair: WordPair)

    @Query("DELETE FROM word_pairs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
