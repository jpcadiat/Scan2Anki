package com.scan2anki.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = ImportSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val imagePath: String,
    val order: Int,
    val ocrState: String = "PENDING",
    val zoneCacheJson: String? = null,
)
