package com.scan2anki.ocr

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudVisionParserTest {

    private val sampleJson = """
        {
          "responses": [
            {
              "fullTextAnnotation": {
                "pages": [
                  {
                    "blocks": [
                      {
                        "paragraphs": [
                          {
                            "words": [
                              {
                                "boundingBox": { "vertices": [ { "x": 50, "y": 20 }, { "x": 100, "y": 20 }, { "x": 100, "y": 40 }, { "x": 50, "y": 40 } ] },
                                "symbols": [ { "text": "c" }, { "text": "a" }, { "text": "t" } ]
                              },
                              {
                                "boundingBox": { "vertices": [ { "x": 300, "y": 22 }, { "x": 380, "y": 22 }, { "x": 380, "y": 42 }, { "x": 300, "y": 42 } ] },
                                "symbols": [ { "text": "g" }, { "text": "a" }, { "text": "t" }, { "text": "o" } ]
                              }
                            ]
                          },
                          {
                            "words": [
                              {
                                "boundingBox": { "vertices": [ { "x": 50, "y": 100 }, { "x": 110, "y": 100 }, { "x": 110, "y": 120 }, { "x": 50, "y": 120 } ] },
                                "symbols": [ { "text": "d" }, { "text": "o" }, { "text": "g" } ]
                              },
                              {
                                "boundingBox": { "vertices": [ { "x": 300, "y": 102 }, { "x": 400, "y": 102 }, { "x": 400, "y": 122 }, { "x": 300, "y": 122 } ] },
                                "symbols": [ { "text": "p" }, { "text": "e" }, { "text": "r" }, { "text": "r" }, { "text": "o" } ]
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parse_reconstructsLinesWithBoundingBoxes() {
        val result = CloudVisionParser.parse(sampleJson, imageWidth = 1000, imageHeight = 500)
        assertThat(result.source).isEqualTo(OcrSource.CLOUD)
        assertThat(result.lines).hasSize(4)
        assertThat(result.lines[0].text).isEqualTo("cat")
        assertThat(result.lines[0].left).isEqualTo(0.05f)
        assertThat(result.lines[0].top).isEqualTo(0.04f)
        assertThat(result.lines[0].right).isEqualTo(0.10f)
        assertThat(result.lines[0].bottom).isEqualTo(0.08f)
        assertThat(result.lines[1].text).isEqualTo("gato")
        assertThat(result.lines[1].left).isEqualTo(0.30f)
        assertThat(result.lines[2].text).isEqualTo("dog")
        assertThat(result.lines[3].text).isEqualTo("perro")
    }

    @Test
    fun parse_wordsCloseTogether_stayOnOneLine() {
        val json = """
            {
              "responses": [
                {
                  "fullTextAnnotation": {
                    "pages": [
                      {
                        "blocks": [
                          {
                            "paragraphs": [
                              {
                                "words": [
                                  {
                                    "boundingBox": { "vertices": [ { "x": 50, "y": 20 }, { "x": 100, "y": 20 }, { "x": 100, "y": 40 }, { "x": 50, "y": 40 } ] },
                                    "symbols": [ { "text": "h" }, { "text": "e" }, { "text": "l" }, { "text": "l" }, { "text": "o" } ]
                                  },
                                  {
                                    "boundingBox": { "vertices": [ { "x": 110, "y": 22 }, { "x": 180, "y": 22 }, { "x": 180, "y": 42 }, { "x": 110, "y": 42 } ] },
                                    "symbols": [ { "text": "w" }, { "text": "o" }, { "text": "r" }, { "text": "l" }, { "text": "d" } ]
                                  }
                                ]
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        val result = CloudVisionParser.parse(json, 1000, 500)
        assertThat(result.lines).hasSize(1)
        assertThat(result.lines[0].text).isEqualTo("hello world")
    }

    @Test
    fun parse_emptyResponses_returnsEmptyResult() {
        val result = CloudVisionParser.parse("{\"responses\":[]}", 1000, 500)
        assertThat(result.lines).isEmpty()
    }

    @Test
    fun parse_apiErrorResponse_throwsWithMessage() {
        val errorJson = """
            {
              "error": {
                "code": 400,
                "message": "API key not valid.",
                "status": "INVALID_ARGUMENT"
              }
            }
        """.trimIndent()
        val exception = assertThrows(IOException::class.java) {
            CloudVisionParser.parse(errorJson, 1000, 500)
        }
        assertThat(exception.message).contains("API key not valid")
    }

    @Test
    fun parse_perImageError_throwsWithResponseMessage() {
        val errorJson = """
            {
              "responses": [
                {
                  "error": {
                    "code": 403,
                    "message": "Requests from this Android client application are blocked."
                  }
                }
              ]
            }
        """.trimIndent()
        val exception = assertThrows(IOException::class.java) {
            CloudVisionParser.parse(errorJson, 1000, 500)
        }
        assertThat(exception.message).contains("Requests from this Android client application are blocked")
    }

    @Test
    fun parse_perImageError_withoutMessage_reportsCode() {
        val errorJson = """{"responses":[{"error":{"code":500}}]}"""
        val exception = assertThrows(IOException::class.java) {
            CloudVisionParser.parse(errorJson, 1000, 500)
        }
        assertThat(exception.message).contains("Cloud Vision API error 500")
    }

    @Test
    fun parse_perImageError_whenNoResponse_errorNotThrown() {
        val result = CloudVisionParser.parse("{\"responses\":[{}]}", 1000, 500)
        assertThat(result.lines).isEmpty()
    }

    @Test
    fun buildRequestJson_containsBase64AndTextDetection() {
        val json = CloudVisionParser.buildRequestJson("QUJD")
        val obj = JSONObject(json)
        val request = obj.getJSONArray("requests").getJSONObject(0)
        assertThat(request.getJSONObject("image").getString("content")).isEqualTo("QUJD")
        assertThat(request.getJSONArray("features").getJSONObject(0).getString("type")).isEqualTo("TEXT_DETECTION")
    }

    @Test
    fun errorMessage_topLevelError_returnsMessage() {
        val errorJson = """
            {
              "error": {
                "code": 403,
                "message": "This API method requires billing to be enabled. Please enable billing on project 123456789012 by visiting https://console.developers.google.com/billing/enable?project=123456789012 then retry.",
                "status": "PERMISSION_DENIED"
              }
            }
        """.trimIndent()

        val message = CloudVisionParser.errorMessage(errorJson)

        assertThat(message).contains("billing to be enabled")
        assertThat(message).contains("https://console.developers.google.com/billing/enable?project=123456789012")
    }

    @Test
    fun errorMessage_noErrorField_returnsNull() {
        val message = CloudVisionParser.errorMessage("{}")

        assertThat(message).isNull()
    }

    @Test
    fun errorMessage_invalidJson_returnsNull() {
        val message = CloudVisionParser.errorMessage("not json")

        assertThat(message).isNull()
    }
}
