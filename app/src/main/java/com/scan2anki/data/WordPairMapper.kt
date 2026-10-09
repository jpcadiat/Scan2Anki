package com.scan2anki.data

import com.scan2anki.parse.ColumnParser
import timber.log.Timber

object WordPairMapper {
    fun fromRow(sessionId: Long, pageId: Long?, order: Int, row: ColumnParser.ParsedRow): WordPair {
        val pair = WordPair(
            sessionId = sessionId,
            pageId = pageId,
            front = row.front,
            back = row.back,
            order = order,
            isUnpaired = row.isUnpaired,
        )
        Timber.v(
            "Mapped row %d -> WordPair(id=%d, unpaired=%b)",
            order, pair.id, pair.isUnpaired,
        )
        return pair
    }
}
