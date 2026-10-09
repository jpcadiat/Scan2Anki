package com.scan2anki.data

import com.google.common.truth.Truth.assertThat
import com.scan2anki.ocr.OcrLine
import com.scan2anki.parse.ColumnParser.ZoneRect
import org.junit.Test

class PageZoneCacheCodecTest {

    @Test
    fun encodeThenDecode_roundTripsAllFields() {
        val cache = PageZoneCache(
            imageWidth = 1200,
            imageHeight = 1600,
            lines = listOf(
                OcrLine("cat", 0.05f, 0.10f, 0.30f, 0.13f),
                OcrLine("gato", 0.55f, 0.10f, 0.80f, 0.13f),
            ),
            splitX = 0.42f,
            ignoreZones = listOf(ZoneRect(0.0f, 0.0f, 0.4f, 0.08f)),
            deletedLines = listOf(2, 5),
        )

        val json = PageZoneCacheCodec.encode(cache)
        val decoded = PageZoneCacheCodec.decode(json)

        assertThat(decoded).isEqualTo(cache)
    }

    @Test
    fun decode_null_returnsNull() {
        assertThat(PageZoneCacheCodec.decode(null)).isNull()
    }

    @Test
    fun decode_blank_returnsNull() {
        assertThat(PageZoneCacheCodec.decode("   ")).isNull()
    }

    @Test
    fun decode_malformedJson_returnsNullInsteadOfThrowing() {
        assertThat(PageZoneCacheCodec.decode("not valid json")).isNull()
    }
}
