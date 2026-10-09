package com.scan2anki.parse

import com.scan2anki.ocr.OcrLine
import com.scan2anki.ocr.OcrResult
import kotlinx.serialization.Serializable
import timber.log.Timber

object ColumnParser {

    private const val COLUMN_GAP_THRESHOLD = 0.03f
    private const val FULL_WIDTH_FRACTION = 0.5f

    enum class LineRegion { LEFT, RIGHT, FULL_WIDTH }

    @Serializable
    data class ZoneRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        fun contains(x: Float, y: Float): Boolean =
            x >= left && x <= right && y >= top && y <= bottom
    }

    data class ParsedRow(
        val front: String,
        val back: String,
        val isUnpaired: Boolean = false,
    )

    private data class RowWithY(
        val front: String,
        val back: String,
        val isUnpaired: Boolean,
        val y: Float,
    )

    fun parse(result: OcrResult): List<ParsedRow> {
        val lines = result.lines.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) {
            Timber.w("OCR result from %s has no non-blank lines", result.source)
            return emptyList()
        }
        return parseWithLayout(lines, detectColumnSplit(lines), emptyList())
    }

    fun parseWithLayout(
        lines: List<OcrLine>,
        splitX: Float?,
        ignoreZones: List<ZoneRect>,
    ): List<ParsedRow> {
        val nonBlank = lines.filter { it.text.isNotBlank() }
        if (nonBlank.isEmpty()) {
            Timber.w("OCR result has no non-blank lines")
            return emptyList()
        }
        val kept = nonBlank.filter { line ->
            ignoreZones.none { zone -> zone.contains(line.centerX, line.centerY) }
        }
        if (kept.size != nonBlank.size) {
            Timber.d("Ignored %d line(s) inside ignore zones", nonBlank.size - kept.size)
        }
        if (kept.isEmpty()) {
            Timber.w("All lines were excluded by ignore zones")
            return emptyList()
        }
        val (fullWidth, columnLines) = kept.partition { it.right - it.left >= FULL_WIDTH_FRACTION }
        val rows = mutableListOf<RowWithY>()
        rows += fullWidth.map { RowWithY(it.text, "", isUnpaired = true, y = it.centerY) }
        if (splitX == null) {
            Timber.d("No column split; treating %d line(s) as unpaired", columnLines.size)
            rows += columnLines.map { RowWithY(it.text, "", isUnpaired = true, y = it.centerY) }
        } else {
            val left = columnLines.filter { it.centerX < splitX }
            val right = columnLines.filter { it.centerX >= splitX }
            Timber.d("Splitting columns at x=%.4f: %d left, %d right", splitX, left.size, right.size)
            rows += pairRows(left.sortedBy { it.centerY }, right.sortedBy { it.centerY })
        }

        val sorted = rows.sortedBy { it.y }
        val withoutHeader = if (sorted.isNotEmpty() && isLikelyHeader(sorted[0].front, sorted[0].back)) {
            Timber.d("Dropping header row: %s / %s", sorted[0].front, sorted[0].back)
            sorted.drop(1)
        } else {
            sorted
        }
        val parsed = withoutHeader.map { row ->
            ParsedRow(
                front = row.front,
                back = row.back,
                isUnpaired = row.isUnpaired,
            )
        }
        val unpaired = parsed.count { it.isUnpaired }
        if (unpaired > 0) Timber.w("%d of %d row(s) are unpaired", unpaired, parsed.size)
        Timber.i("ColumnParser produced %d row(s)", parsed.size)
        return parsed
    }

    fun detectColumnSplit(lines: List<OcrLine>): Float? {
        val candidates = lines.filter { it.right - it.left < FULL_WIDTH_FRACTION }
        if (candidates.size < 2) {
            Timber.v("Fewer than 2 candidate line(s) for column split")
            return null
        }
        val sorted = candidates.sortedBy { it.left }
        var bestIdx = -1
        var bestGap = 0f
        for (i in 1 until sorted.size) {
            val gap = sorted[i].left - sorted[i - 1].right
            if (gap > bestGap) {
                bestGap = gap
                bestIdx = i
            }
        }
        if (bestIdx < 0 || bestGap < COLUMN_GAP_THRESHOLD) {
            Timber.d("Largest column gap %.4f below threshold %.4f; no split", bestGap, COLUMN_GAP_THRESHOLD)
            return null
        }
        val splitX = (sorted[bestIdx - 1].left + sorted[bestIdx].left) / 2f
        Timber.d("Splitting columns at x=%.4f with gap %.4f", splitX, bestGap)
        return splitX
    }

    fun splitColumns(lines: List<OcrLine>): Pair<List<OcrLine>, List<OcrLine>> {
        val splitX = detectColumnSplit(lines) ?: return lines to emptyList()
        return lines.filter { it.centerX < splitX } to lines.filter { it.centerX >= splitX }
    }

    fun classifyLines(lines: List<OcrLine>, splitX: Float?): List<LineRegion> =
        lines.map { line ->
            when {
                line.right - line.left >= FULL_WIDTH_FRACTION -> LineRegion.FULL_WIDTH
                splitX == null -> LineRegion.LEFT
                line.centerX < splitX -> LineRegion.LEFT
                else -> LineRegion.RIGHT
            }
        }

    /**
     * Matches lines from two columns (each already sorted by [OcrLine.centerY]) by index, up to
     * the length of the shorter list. Exposed so callers that need the paired geometry (not just
     * paired text) can reuse the same matching as [parseWithLayout].
     */
    fun pairLines(left: List<OcrLine>, right: List<OcrLine>): List<Pair<OcrLine, OcrLine>> =
        left.zip(right)

    private fun pairRows(left: List<OcrLine>, right: List<OcrLine>): List<RowWithY> {
        val rows = mutableListOf<RowWithY>()
        val matched = pairLines(left, right)
        rows += matched.map { (front, back) ->
            RowWithY(
                front = front.text,
                back = back.text,
                isUnpaired = false,
                y = (front.centerY + back.centerY) / 2f,
            )
        }
        val paired = matched.size
        for (i in paired until left.size) {
            rows.add(RowWithY(left[i].text, "", isUnpaired = true, y = left[i].centerY))
        }
        for (j in paired until right.size) {
            rows.add(RowWithY("", right[j].text, isUnpaired = true, y = right[j].centerY))
        }
        return rows
    }

    fun isLikelyHeader(front: String, back: String): Boolean {
        val f = front.trim()
        val b = back.trim()
        if (f.isEmpty() || b.isEmpty()) return false
        if (f.length > 14 || b.length > 14) return false
        val letters = f + b
        if (letters.none { it.isLetter() }) return false
        val allCaps = letters == letters.uppercase()
        val titleCase = listOf(f, b).all { cell ->
            cell.split(' ').all { word ->
                word.isNotEmpty() && word.first().isUpperCase() && word.drop(1).all { !it.isUpperCase() }
            }
        }
        val result = allCaps || titleCase
        Timber.v("Header detection for \"%s | %s\": %b", f, b, result)
        return result
    }
}