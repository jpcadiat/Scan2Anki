package com.scan2anki.ankidroid

/** Runtime permission defined by AnkiDroid that grants third-party apps read/write access. */
const val ANKI_READ_WRITE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"

data class AnkiNote(
    val front: String,
    val back: String,
)

data class AnkiDroidDiagnosis(
    val installed: Boolean,
    val apiAvailable: Boolean,
    val message: String,
) {
    val ok: Boolean get() = installed && apiAvailable
}

sealed interface AnkiDroidSendResult {
    data class Added(val added: Int, val total: Int) : AnkiDroidSendResult
    data class Failed(val reason: String) : AnkiDroidSendResult
}

interface AnkiDroidSender {
    fun diagnose(): AnkiDroidDiagnosis

    /** Sends all notes in one batch request. */
    fun sendCards(deckName: String, noteTypeName: String, notes: List<AnkiNote>): AnkiDroidSendResult

    /** Lists AnkiDroid's local deck names. Never throws — returns an empty list on failure. */
    fun getDeckNames(): List<String>

    fun getNoteTypeNames(): List<String>
}