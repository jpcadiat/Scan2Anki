package com.scan2anki.ocr

import com.google.mlkit.vision.text.Text
import timber.log.Timber

object MlKitTextMapper {
    fun map(text: Text, width: Int, height: Int): OcrResult {
        val lines = text.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(
                    text = line.text,
                    left = box.left / width.toFloat(),
                    top = box.top / height.toFloat(),
                    right = box.right / width.toFloat(),
                    bottom = box.bottom / height.toFloat(),
                )
            }
        Timber.d("Mapped %d ML Kit line(s) to %d OcrLine(s)", text.textBlocks.size, lines.size)
        return OcrResult(lines, OcrSource.ON_DEVICE)
    }
}