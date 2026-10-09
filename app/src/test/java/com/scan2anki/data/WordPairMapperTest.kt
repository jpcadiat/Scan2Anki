package com.scan2anki.data

import com.google.common.truth.Truth.assertThat
import com.scan2anki.parse.ColumnParser
import org.junit.Test

class WordPairMapperTest {

    @Test
    fun fromRow_mapsAllFieldsCorrectly() {
        val row = ColumnParser.ParsedRow(
            front = "der Hund",
            back = "dog",
            isUnpaired = false,
        )

        val pair = WordPairMapper.fromRow(
            sessionId = 42L,
            pageId = 10L,
            order = 3,
            row = row,
        )

        assertThat(pair.sessionId).isEqualTo(42L)
        assertThat(pair.pageId).isEqualTo(10L)
        assertThat(pair.order).isEqualTo(3)
        assertThat(pair.front).isEqualTo("der Hund")
        assertThat(pair.back).isEqualTo("dog")
        assertThat(pair.isHeader).isFalse()
        assertThat(pair.isUnpaired).isFalse()
    }

    @Test
    fun fromRow_withUnpairedAndNullPageId_mapsCorrectly() {
        val row = ColumnParser.ParsedRow(
            front = "German",
            back = "English",
            isUnpaired = true,
        )

        val pair = WordPairMapper.fromRow(
            sessionId = 1L,
            pageId = null,
            order = 0,
            row = row,
        )

        assertThat(pair.sessionId).isEqualTo(1L)
        assertThat(pair.pageId).isNull()
        assertThat(pair.order).isEqualTo(0)
        assertThat(pair.front).isEqualTo("German")
        assertThat(pair.back).isEqualTo("English")
        assertThat(pair.isHeader).isFalse()
        assertThat(pair.isUnpaired).isTrue()
    }
}
