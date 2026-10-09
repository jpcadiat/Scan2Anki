package com.scan2anki.ocr

import kotlinx.serialization.Serializable

enum class OcrSource { ON_DEVICE, CLOUD }

@Serializable
data class OcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun normalized(width: Int, height: Int): OcrLine =
        OcrLine(text, left / width, top / height, right / width, bottom / height)
}

data class OcrResult(
    val lines: List<OcrLine>,
    val source: OcrSource,
)
