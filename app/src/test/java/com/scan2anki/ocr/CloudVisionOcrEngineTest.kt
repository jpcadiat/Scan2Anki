package com.scan2anki.ocr

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloudVisionOcrEngineTest {

    private fun clientReturning(code: Int, body: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

    @Test
    fun testKey_success_returnsSuccess() = runTest {
        val engine = CloudVisionOcrEngine(clientReturning(200, "{\"responses\":[{}]}")) { "fake-key" }

        val result = engine.testKey()

        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun testKey_visionApiError_returnsFailureWithMessage() = runTest {
        val body = """{"responses":[{"error":{"code":403,"message":"API key not valid."}}]}"""
        val engine = CloudVisionOcrEngine(clientReturning(200, body)) { "fake-key" }

        val result = engine.testKey()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("API key not valid")
    }

    @Test
    fun testKey_httpError_returnsFailureWithCode() = runTest {
        val engine = CloudVisionOcrEngine(clientReturning(400, "{}")) { "fake-key" }

        val result = engine.testKey()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("400")
    }

    @Test
    fun testKey_httpErrorWithApiMessage_returnsFailureWithApiMessage() = runTest {
        val body = """
            {
              "error": {
                "code": 403,
                "message": "This API method requires billing to be enabled. Please enable billing on project 123456789012 by visiting https://console.developers.google.com/billing/enable?project=123456789012 then retry.",
                "status": "PERMISSION_DENIED"
              }
            }
        """.trimIndent()
        val engine = CloudVisionOcrEngine(clientReturning(403, body)) { "fake-key" }

        val result = engine.testKey()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("billing to be enabled")
    }

    @Test
    fun testKey_blankApiKey_returnsFailure() = runTest {
        val engine = CloudVisionOcrEngine(clientReturning(200, "{\"responses\":[{}]}")) { "" }

        val result = engine.testKey()

        assertThat(result.isFailure).isTrue()
    }
}
