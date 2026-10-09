package com.scan2anki.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

class MlKitOcrEngine(
    private val scriptFlow: Flow<String>,
) : OcrEngine {

    private val recognizers: Map<String, TextRecognizer> = mapOf(
        "latin" to TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
        "chinese" to TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
    )

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        val script = scriptFlow.first()
        val recognizer = if (recognizers.containsKey(script)) {
            Timber.v("Using on-device recognizer for script \"%s\"", script)
            recognizers.getValue(script)
        } else {
            Timber.w("Unknown OCR script \"%s\", falling back to latin", script)
            recognizers.getValue("latin")
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        Timber.d("Running on-device OCR on %dx%d image", bitmap.width, bitmap.height)
        val text = withContext(Dispatchers.IO) { Tasks.await(recognizer.process(image)) }
        val result = MlKitTextMapper.map(text, bitmap.width, bitmap.height)
        Timber.i("On-device OCR produced %d line(s)", result.lines.size)
        return result
    }
}