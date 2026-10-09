package com.scan2anki.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_sessions")
data class ImportSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val deckName: String? = null,
    val status: String = "IN_PROGRESS",
)
