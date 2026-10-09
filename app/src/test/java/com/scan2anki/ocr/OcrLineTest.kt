package com.scan2anki.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OcrLineTest {

    @Test
    fun centerX_isMidpoint() {
        val line = OcrLine(text = "x", left = 0.1f, top = 0.2f, right = 0.3f, bottom = 0.4f)
        assertThat(line.centerX).isEqualTo(0.2f)
    }

    @Test
    fun centerY_isMidpoint() {
        val line = OcrLine(text = "x", left = 0.1f, top = 0.2f, right = 0.3f, bottom = 0.4f)
        assertThat(line.centerY).isEqualTo(0.3f)
    }

    @Test
    fun normalized_scalesByImageDimensions() {
        val line = OcrLine(text = "x", left = 100f, top = 50f, right = 200f, bottom = 150f)
        val normalized = line.normalized(width = 1000, height = 500)
        assertThat(normalized.left).isEqualTo(0.1f)
        assertThat(normalized.top).isEqualTo(0.1f)
        assertThat(normalized.right).isEqualTo(0.2f)
        assertThat(normalized.bottom).isEqualTo(0.3f)
    }
}
