package com.scan2anki.ocr

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

class CloudVisionOcrEngine(
    private val client: OkHttpClient,
    private val getApiKey: suspend () -> String,
) : OcrEngine {

    override suspend fun recognize(bitmap: Bitmap): OcrResult {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            Timber.e("Cloud OCR requested but API key is not configured")
        }
        require(apiKey.isNotBlank()) { "Cloud OCR API key not configured" }
        Timber.d("Cloud OCR request for %dx%d image", bitmap.width, bitmap.height)
        val base64 = bitmap.toJpegBase64()
        Timber.v("Cloud OCR request body is %d bytes (base64)", base64.length)
        val body = CloudVisionParser.buildRequestJson(base64)
        val request = Request.Builder()
            .url("https://vision.googleapis.com/v1/images:annotate?key=$apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string()
                if (!response.isSuccessful) {
                    val apiMessage = bodyText?.let { CloudVisionParser.errorMessage(it) }
                    Timber.e("Cloud Vision API returned HTTP %d: %s", response.code, bodyText)
                    throw IOException(apiMessage ?: "Vision API returned ${response.code}")
                }
                if (bodyText == null) {
                    Timber.wtf("Cloud Vision API returned successful response with null body")
                    throw IOException("Vision API returned empty body")
                }
                Timber.d("Cloud Vision API returned %d bytes", bodyText.length)
                CloudVisionParser.parse(bodyText, bitmap.width, bitmap.height)
            }
        }
    }

    /** Runs the same request path as [recognize] against a tiny throwaway image, to validate the key. */
    suspend fun testKey(): Result<Unit> {
        Timber.d("Testing Cloud Vision API key")
        return runCatching {
            val probe = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            recognize(probe)
        }.map { }
    }

    private fun Bitmap.toJpegBase64(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 90, stream)
        Timber.v("Encoded bitmap to %d bytes of JPEG", stream.size())
        return Base64.getEncoder().encodeToString(stream.toByteArray())
    }
}