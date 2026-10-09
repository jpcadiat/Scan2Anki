package com.scan2anki.parse

import com.google.common.truth.Truth.assertThat
import com.scan2anki.data.WordPair
import com.scan2anki.parse.OcrCleanup.CasingMode
import com.scan2anki.parse.OcrCleanup.CasingRule
import com.scan2anki.parse.OcrCleanup.CleanupConfig
import com.scan2anki.parse.OcrCleanup.ColumnScope
import com.scan2anki.parse.OcrCleanup.PageNumberRule
import com.scan2anki.parse.OcrCleanup.SeparatorCutRule
import com.scan2anki.parse.OcrCleanup.TrimJunkRule
import org.junit.Test

class OcrCleanupTest {

    private fun pair(front: String, back: String) =
        WordPair(sessionId = 1L, front = front, back = back, order = 0)

    // trimJunk

    @Test
    fun trimJunk_stripsNonAlphanumericFromBothEnds() {
        assertThat(OcrCleanup.trimJunk("- banana. ", "")).isEqualTo("banana")
    }

    @Test
    fun trimJunk_keepsInteriorPunctuation() {
        assertThat(OcrCleanup.trimJunk("*don't*", "")).isEqualTo("don't")
    }

    @Test
    fun trimJunk_alsoStripsUserSuppliedExtraChars() {
        assertThat(OcrCleanup.trimJunk("1banana1", "1")).isEqualTo("banana")
    }

    // stripTrailingPageNumber

    @Test
    fun stripTrailingPageNumber_removesNumberSeparatedBySpace() {
        assertThat(OcrCleanup.stripTrailingPageNumber("banana 45")).isEqualTo("banana")
    }

    @Test
    fun stripTrailingPageNumber_removesNumberSeparatedByPunctuation() {
        assertThat(OcrCleanup.stripTrailingPageNumber("banana - 45")).isEqualTo("banana")
    }

    @Test
    fun stripTrailingPageNumber_leavesDigitsGluedToWordAlone() {
        assertThat(OcrCleanup.stripTrailingPageNumber("B12")).isEqualTo("B12")
    }

    @Test
    fun stripTrailingPageNumber_leavesDigitsOnlyCellAlone() {
        assertThat(OcrCleanup.stripTrailingPageNumber("45")).isEqualTo("45")
    }

    @Test
    fun stripTrailingPageNumber_doesNotEatClosingParenOfAWordComment() {
        assertThat(OcrCleanup.stripTrailingPageNumber("apple (fruit) 12")).isEqualTo("apple (fruit)")
    }

    @Test
    fun stripTrailingPageNumber_removesCommaSeparatedPageList() {
        assertThat(OcrCleanup.stripTrailingPageNumber("apple (fruit) 2, 5")).isEqualTo("apple (fruit)")
    }

    // cutAfterSeparator

    @Test
    fun cutAfterSeparator_cutsAtFirstOccurrenceAndTrimsTrailingSpace() {
        assertThat(OcrCleanup.cutAfterSeparator("apple (fruit)", "(")).isEqualTo("apple")
    }

    @Test
    fun cutAfterSeparator_leavesTextUnchangedWhenSeparatorAbsent() {
        assertThat(OcrCleanup.cutAfterSeparator("apple", "(")).isEqualTo("apple")
    }

    // fixCasing

    @Test
    fun fixCasing_sentenceCasesText() {
        assertThat(OcrCleanup.fixCasing("BANANA", CasingMode.SENTENCE)).isEqualTo("Banana")
    }

    @Test
    fun fixCasing_lowercasesText() {
        assertThat(OcrCleanup.fixCasing("BANANA", CasingMode.LOWERCASE)).isEqualTo("banana")
    }

    // apply() pipeline

    @Test
    fun apply_respectsFrontOnlyScope() {
        val config = CleanupConfig(
            casing = CasingRule(enabled = true, scope = ColumnScope.FRONT, mode = CasingMode.LOWERCASE),
        )
        val result = OcrCleanup.apply(pair("BANANA", "MANZANA"), config)
        assertThat(result.front).isEqualTo("banana")
        assertThat(result.back).isEqualTo("MANZANA")
    }

    @Test
    fun apply_respectsBackOnlyScope() {
        val config = CleanupConfig(
            casing = CasingRule(enabled = true, scope = ColumnScope.BACK, mode = CasingMode.LOWERCASE),
        )
        val result = OcrCleanup.apply(pair("BANANA", "MANZANA"), config)
        assertThat(result.front).isEqualTo("BANANA")
        assertThat(result.back).isEqualTo("manzana")
    }

    @Test
    fun apply_alwaysTrimsWhitespaceEvenWithNoRulesEnabled() {
        val result = OcrCleanup.apply(pair("  banana  ", "  manzana  "), CleanupConfig())
        assertThat(result.front).isEqualTo("banana")
        assertThat(result.back).isEqualTo("manzana")
    }

    @Test
    fun apply_chainsMultipleEnabledRulesInOrder() {
        val config = CleanupConfig(
            trimJunk = TrimJunkRule(enabled = true, scope = ColumnScope.BOTH),
            pageNumber = PageNumberRule(enabled = true, scope = ColumnScope.BOTH),
            separatorCut = SeparatorCutRule(enabled = true, scope = ColumnScope.BOTH, separator = ","),
            casing = CasingRule(enabled = true, scope = ColumnScope.BOTH, mode = CasingMode.SENTENCE),
        )
        val result = OcrCleanup.apply(pair("- BANANA, extra - 45", "manzana"), config)
        assertThat(result.front).isEqualTo("Banana")
        assertThat(result.back).isEqualTo("Manzana")
    }

    // preview()

    @Test
    fun preview_returnsOnlyChangedPairs() {
        val unchanged = pair("banana", "manzana")
        val changed = pair("BANANA", "manzana")
        val config = CasingRule(enabled = true, scope = ColumnScope.FRONT, mode = CasingMode.LOWERCASE)
            .let { CleanupConfig(casing = it) }

        val result = OcrCleanup.preview(listOf(unchanged, changed), config)

        assertThat(result).hasSize(1)
        assertThat(result[0].first).isEqualTo(changed)
        assertThat(result[0].second.front).isEqualTo("banana")
    }

    // CleanupConfigCodec

    @Test
    fun cleanupConfigCodec_encodeThenDecode_roundTripsAllFields() {
        val config = CleanupConfig(
            trimJunk = TrimJunkRule(enabled = true, scope = ColumnScope.BACK, extraChars = "#~"),
            pageNumber = PageNumberRule(enabled = true, scope = ColumnScope.FRONT),
            separatorCut = SeparatorCutRule(enabled = true, scope = ColumnScope.BOTH, separator = ";"),
            casing = CasingRule(enabled = true, scope = ColumnScope.BACK, mode = CasingMode.LOWERCASE),
        )

        val json = CleanupConfigCodec.encode(config)
        val decoded = CleanupConfigCodec.decode(json)

        assertThat(decoded).isEqualTo(config)
    }

    @Test
    fun cleanupConfigCodec_decode_null_returnsDefaults() {
        assertThat(CleanupConfigCodec.decode(null)).isEqualTo(CleanupConfig())
    }

    @Test
    fun cleanupConfigCodec_decode_blank_returnsDefaults() {
        assertThat(CleanupConfigCodec.decode("   ")).isEqualTo(CleanupConfig())
    }

    @Test
    fun cleanupConfigCodec_decode_malformedJson_returnsDefaultsInsteadOfThrowing() {
        assertThat(CleanupConfigCodec.decode("not valid json")).isEqualTo(CleanupConfig())
    }
}
