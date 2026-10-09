package com.scan2anki.ocr

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

object CloudVisionParser {

    private const val LINE_GROUP_TOLERANCE = 0.02f
    private const val INTERNAL_GAP_THRESHOLD = 0.03f
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private data class WordBox(
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val centerY: Float get() = (top + bottom) / 2f
    }

    fun parse(jsonString: String, imageWidth: Int, imageHeight: Int): OcrResult {
        Timber.d("Parsing Cloud Vision response (%d bytes)", jsonString.length)
        val response = json.decodeFromString<CloudVisionResponse>(jsonString)
        response.error?.let { error ->
            val message = error.message.ifBlank { error.status }.ifBlank { "code ${error.code}" }
            Timber.e("Cloud Vision API error: %s", message)
            throw IOException("Cloud Vision API error: $message")
        }
        val firstResponse = response.responses.firstOrNull()
        firstResponse?.error?.let { error ->
            val message = error.message ?: "Cloud Vision API error ${error.code}"
            Timber.e("Cloud Vision per-request error: %s", message)
            throw IOException(message)
        }
        val words = firstResponse?.fullTextAnnotation?.pages.orEmpty()
            .flatMap { it.blocks }
            .flatMap { it.paragraphs }
            .flatMap { it.words }
            .mapNotNull { it.toWordBox(imageWidth, imageHeight) }
        Timber.d("Extracted %d word box(es) from response", words.size)
        if (words.isEmpty()) {
            Timber.w("Cloud Vision returned no text for image")
            return OcrResult(emptyList(), OcrSource.CLOUD)
        }

        val lines = groupIntoLines(words).map { box ->
            OcrLine(
                text = box.text,
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom,
            )
        }
        Timber.i("Cloud Vision grouped %d word(s) into %d line(s)", words.size, lines.size)
        return OcrResult(lines, OcrSource.CLOUD)
    }

    /** Decodes a Cloud Vision response body's top-level `error` field, if present. */
    fun errorMessage(jsonString: String): String? {
        val error = try {
            json.decodeFromString<CloudVisionResponse>(jsonString).error
        } catch (e: SerializationException) {
            null
        } ?: return null
        return error.message.ifBlank { error.status }.ifBlank { "code ${error.code}" }
    }

    fun buildRequestJson(base64Image: String): String {
        Timber.v("Building Cloud Vision request JSON")
        val request = VisionRequest(
            requests = listOf(
                VisionRequestItem(
                    image = VisionImage(content = base64Image),
                    features = listOf(VisionFeature()),
                ),
            ),
        )
        return json.encodeToString(request)
    }

    private fun VisionWord.toWordBox(imageWidth: Int, imageHeight: Int): WordBox? {
        val vertices = boundingBox?.vertices.orEmpty()
        if (vertices.size < 4) {
            Timber.v("Skipping word with %d vertex(es) (<4)", vertices.size)
            return null
        }
        val xs = vertices.mapNotNull { it.x }
        val ys = vertices.mapNotNull { it.y }
        if (xs.isEmpty() || ys.isEmpty()) return null
        val text = symbols.joinToString("") { it.text }
        if (text.isBlank()) return null
        return WordBox(
            text = text,
            left = xs.min().toFloat() / imageWidth,
            top = ys.min().toFloat() / imageHeight,
            right = xs.max().toFloat() / imageWidth,
            bottom = ys.max().toFloat() / imageHeight,
        )
    }

    private fun groupIntoLines(words: List<WordBox>): List<WordBox> {
        val sorted = words.sortedBy { it.centerY }
        val lines = mutableListOf<MutableList<WordBox>>()
        for (word in sorted) {
            val current = lines.lastOrNull()
            val meanY = current?.map { it.centerY }?.average()?.toFloat() ?: Float.MAX_VALUE
            if (current != null && kotlin.math.abs(word.centerY - meanY) <= LINE_GROUP_TOLERANCE) {
                current.add(word)
            } else {
                lines.add(mutableListOf(word))
            }
        }
        return lines.flatMap { splitLine(it) }
    }

    private fun splitLine(lineWords: List<WordBox>): List<WordBox> {
        val sorted = lineWords.sortedBy { it.left }
        if (sorted.size < 2) return listOf(boxFrom(sorted))
        var bestIdx = -1
        var bestGap = 0f
        for (i in 1 until sorted.size) {
            val gap = sorted[i].left - sorted[i - 1].right
            if (gap > bestGap) {
                bestGap = gap
                bestIdx = i
            }
        }
        if (bestIdx < 0 || bestGap < INTERNAL_GAP_THRESHOLD) return listOf(boxFrom(sorted))
        Timber.d("Splitting line at word %d with gap %.4f", bestIdx, bestGap)
        return listOf(boxFrom(sorted.subList(0, bestIdx)), boxFrom(sorted.subList(bestIdx, sorted.size)))
    }

    private fun boxFrom(words: List<WordBox>): WordBox {
        val sorted = words.sortedBy { it.left }
        return WordBox(
            text = sorted.joinToString(" ") { it.text },
            left = sorted.minOf { it.left },
            top = sorted.minOf { it.top },
            right = sorted.maxOf { it.right },
            bottom = sorted.maxOf { it.bottom },
        )
    }
}