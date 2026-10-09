package com.scan2anki.data

import com.scan2anki.ocr.OcrLine
import com.scan2anki.parse.ColumnParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Everything [com.scan2anki.vm.ZoneEditorViewModel] needs to reconstruct a page's editor state
 * without re-running OCR or decoding the source bitmap again.
 */
@Serializable
data class PageZoneCache(
    val imageWidth: Int,
    val imageHeight: Int,
    val lines: List<OcrLine>,
    val splitX: Float?,
    val ignoreZones: List<ColumnParser.ZoneRect>,
    val deletedLines: List<Int>,
)

object PageZoneCacheCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(cache: PageZoneCache): String = json.encodeToString(PageZoneCache.serializer(), cache)

    fun decode(raw: String?): PageZoneCache? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString(PageZoneCache.serializer(), raw)
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization.SerializationException extends IllegalArgumentException,
            // so this single catch also covers malformed-JSON/schema-mismatch failures.
            Timber.w(e, "Failed to decode cached zone layout")
            null
        }
    }
}
