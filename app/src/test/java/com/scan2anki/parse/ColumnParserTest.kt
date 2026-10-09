package com.scan2anki.parse

import com.google.common.truth.Truth.assertThat
import com.scan2anki.ocr.OcrLine
import com.scan2anki.ocr.OcrResult
import com.scan2anki.ocr.OcrSource
import com.scan2anki.parse.ColumnParser.ZoneRect
import org.junit.Test

class ColumnParserTest {

    private fun line(text: String, left: Float, top: Float, right: Float, bottom: Float) =
        OcrLine(text, left, top, right, bottom)

    private fun twoColumnResult(): OcrResult {
        return OcrResult(
            lines = listOf(
                line("English", 0.05f, 0.02f, 0.30f, 0.05f),
                line("Spanish", 0.55f, 0.02f, 0.80f, 0.05f),
                line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
                line("gato", 0.55f, 0.10f, 0.80f, 0.13f),
                line("dog", 0.05f, 0.18f, 0.30f, 0.21f),
                line("perro", 0.55f, 0.18f, 0.80f, 0.21f),
            ),
            source = OcrSource.ON_DEVICE,
        )
    }

    @Test
    fun parse_twoCleanColumns_dropsHeaderRowAndReturnsPairsInOrder() {
        val rows = ColumnParser.parse(twoColumnResult())
        assertThat(rows).hasSize(2)
        assertThat(rows[0].front).isEqualTo("cat")
        assertThat(rows[0].back).isEqualTo("gato")
        assertThat(rows[1].front).isEqualTo("dog")
        assertThat(rows[1].back).isEqualTo("perro")
    }

    @Test
    fun parse_singleColumn_returnsUnpairedRowsWithEmptyBack() {
        val result = OcrResult(
            lines = listOf(
                line("uno", 0.05f, 0.10f, 0.30f, 0.13f),
                line("dos", 0.05f, 0.18f, 0.30f, 0.21f),
            ),
            source = OcrSource.ON_DEVICE,
        )
        val rows = ColumnParser.parse(result)
        assertThat(rows).hasSize(2)
        assertThat(rows[0].front).isEqualTo("uno")
        assertThat(rows[0].back).isEmpty()
        assertThat(rows[0].isUnpaired).isTrue()
    }

    @Test
    fun parse_blankLinesAreIgnored() {
        val result = OcrResult(
            lines = listOf(
                line("", 0.05f, 0.02f, 0.30f, 0.05f),
                line("   ", 0.55f, 0.02f, 0.80f, 0.05f),
            ),
            source = OcrSource.ON_DEVICE,
        )
        assertThat(ColumnParser.parse(result)).isEmpty()
    }

    @Test
    fun parse_emptyResult_returnsEmpty() {
        assertThat(ColumnParser.parse(OcrResult(emptyList(), OcrSource.ON_DEVICE))).isEmpty()
    }

    @Test
    fun parse_leftLineWithoutMatch_isUnpaired() {
        val result = OcrResult(
            lines = listOf(
                line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
                line("gato", 0.55f, 0.10f, 0.80f, 0.13f),
                line("dog", 0.05f, 0.18f, 0.30f, 0.21f),
            ),
            source = OcrSource.ON_DEVICE,
        )
        val rows = ColumnParser.parse(result)
        assertThat(rows).hasSize(2)
        val dog = rows.first { it.front == "dog" }
        assertThat(dog.back).isEmpty()
        assertThat(dog.isUnpaired).isTrue()
    }

    @Test
    fun parse_rightLineWithoutMatch_isUnpairedWithEmptyFront() {
        val result = OcrResult(
            lines = listOf(
                line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
                line("gato", 0.55f, 0.10f, 0.80f, 0.13f),
                line("perro", 0.55f, 0.18f, 0.80f, 0.21f),
            ),
            source = OcrSource.ON_DEVICE,
        )
        val rows = ColumnParser.parse(result)
        assertThat(rows).hasSize(2)
        val perro = rows.first { it.back == "perro" }
        assertThat(perro.front).isEmpty()
        assertThat(perro.isUnpaired).isTrue()
    }

    @Test
    fun parse_slightVerticalOffset_stillPairs() {
        val result = OcrResult(
            lines = listOf(
                line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
                line("gato", 0.55f, 0.115f, 0.80f, 0.145f),
            ),
            source = OcrSource.ON_DEVICE,
        )
        val rows = ColumnParser.parse(result)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].front).isEqualTo("cat")
        assertThat(rows[0].back).isEqualTo("gato")
        assertThat(rows[0].isUnpaired).isFalse()
    }

    @Test
    fun splitColumns_largeGap_splitsIntoTwo() {
        val lines = listOf(
            line("a", 0.05f, 0.10f, 0.30f, 0.13f),
            line("b", 0.55f, 0.10f, 0.80f, 0.13f),
        )
        val (left, right) = ColumnParser.splitColumns(lines)
        assertThat(left.map { it.text }).containsExactly("a")
        assertThat(right.map { it.text }).containsExactly("b")
    }

    @Test
    fun splitColumns_noGap_returnsSingleColumn() {
        val lines = listOf(
            line("a", 0.05f, 0.10f, 0.30f, 0.13f),
            line("b", 0.10f, 0.18f, 0.35f, 0.21f),
        )
        val (left, right) = ColumnParser.splitColumns(lines)
        assertThat(left.map { it.text }).containsExactly("a", "b")
        assertThat(right).isEmpty()
    }

    @Test
    fun isLikelyHeader_allCaps_returnsTrue() {
        assertThat(ColumnParser.isLikelyHeader("SPANISH", "ENGLISH")).isTrue()
    }

    @Test
    fun isLikelyHeader_titleCase_returnsTrue() {
        assertThat(ColumnParser.isLikelyHeader("Spanish", "English")).isTrue()
    }

    @Test
    fun isLikelyHeader_lowercaseVocab_returnsFalse() {
        assertThat(ColumnParser.isLikelyHeader("gato", "cat")).isFalse()
    }

    @Test
    fun isLikelyHeader_longWords_returnsFalse() {
        assertThat(ColumnParser.isLikelyHeader("unfortunately", "desafortunadamente")).isFalse()
    }

    @Test
    fun parse_variedWordLengths_stillSplits() {
        val result = OcrResult(
            lines = listOf(
                line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
                line("unfortunately", 0.05f, 0.18f, 0.45f, 0.21f),
                line("gato", 0.55f, 0.10f, 0.80f, 0.13f),
                line("desafortunadamente", 0.55f, 0.18f, 0.95f, 0.21f),
            ),
            source = OcrSource.ON_DEVICE,
        )
        val rows = ColumnParser.parse(result)
        assertThat(rows).hasSize(2)
        assertThat(rows[0].front).isEqualTo("cat")
        assertThat(rows[0].back).isEqualTo("gato")
        assertThat(rows[1].front).isEqualTo("unfortunately")
        assertThat(rows[1].back).isEqualTo("desafortunadamente")
    }

    @Test
    fun parse_titleLine_isExcludedAndUnpaired() {
        val result = OcrResult(
            lines = listOf(
                line("Vocabulary List", 0.05f, 0.02f, 0.95f, 0.05f),
                line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
                line("gato", 0.55f, 0.10f, 0.80f, 0.13f),
                line("dog", 0.05f, 0.18f, 0.30f, 0.21f),
                line("perro", 0.55f, 0.18f, 0.80f, 0.21f),
            ),
            source = OcrSource.ON_DEVICE,
        )
        val rows = ColumnParser.parse(result)
        assertThat(rows).hasSize(3)
        val title = rows.first { it.front == "Vocabulary List" }
        assertThat(title.isUnpaired).isTrue()
        assertThat(title.back).isEmpty()
        assertThat(rows.first { it.front == "cat" }.back).isEqualTo("gato")
    }

    @Test
    fun parse_largeLineHeight_usesAdaptiveTolerance() {
        val result = OcrResult(
            lines = listOf(
                line("cat", 0.05f, 0.10f, 0.30f, 0.16f),
                line("gato", 0.55f, 0.16f, 0.80f, 0.22f),
            ),
            source = OcrSource.ON_DEVICE,
        )
        val rows = ColumnParser.parse(result)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].front).isEqualTo("cat")
        assertThat(rows[0].back).isEqualTo("gato")
        assertThat(rows[0].isUnpaired).isFalse()
    }

    @Test
    fun parse_unequalRowCounts_pairsByProximityAndMarksRestUnpaired() {
        val result = OcrResult(
            lines = listOf(
                line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
                line("gato", 0.55f, 0.10f, 0.80f, 0.13f),
                line("dog", 0.05f, 0.18f, 0.30f, 0.21f),
                line("perro", 0.55f, 0.18f, 0.80f, 0.21f),
                line("bird", 0.05f, 0.26f, 0.30f, 0.29f),
            ),
            source = OcrSource.ON_DEVICE,
        )
        val rows = ColumnParser.parse(result)
        assertThat(rows).hasSize(3)
        val bird = rows.first { it.front == "bird" }
        assertThat(bird.isUnpaired).isTrue()
        assertThat(bird.back).isEmpty()
    }

    @Test
    fun detectColumnSplit_singleColumnWithVariedLengths_returnsNull() {
        val lines = listOf(
            line("a", 0.05f, 0.10f, 0.30f, 0.13f),
            line("unfortunately", 0.05f, 0.18f, 0.45f, 0.21f),
            line("b", 0.05f, 0.26f, 0.30f, 0.29f),
        )
        assertThat(ColumnParser.detectColumnSplit(lines)).isNull()
    }

    @Test
    fun classifyLines_assignsRegionsBySplit() {
        val lines = listOf(
            line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
            line("gato", 0.55f, 0.10f, 0.80f, 0.13f),
            line("Vocabulary List", 0.05f, 0.02f, 0.95f, 0.05f),
        )
        val regions = ColumnParser.classifyLines(lines, splitX = 0.30f)
        assertThat(regions).containsExactly(
            ColumnParser.LineRegion.LEFT,
            ColumnParser.LineRegion.RIGHT,
            ColumnParser.LineRegion.FULL_WIDTH,
        ).inOrder()
    }

    @Test
    fun parse_differingLineHeights_pairsByCenterYNotTop() {
        val result = OcrResult(
            lines = listOf(
                line("cow", 0.05f, 0.20f, 0.30f, 0.30f),
                line("cat", 0.05f, 0.22f, 0.30f, 0.24f),
                line("vaca", 0.55f, 0.24f, 0.80f, 0.26f),
                line("gato", 0.55f, 0.21f, 0.80f, 0.25f),
            ),
            source = OcrSource.ON_DEVICE,
        )
        val rows = ColumnParser.parse(result)
        assertThat(rows).hasSize(2)
        val cow = rows.first { it.front == "cow" }
        assertThat(cow.back).isEqualTo("vaca")
        assertThat(cow.isUnpaired).isFalse()
        val cat = rows.first { it.front == "cat" }
        assertThat(cat.back).isEqualTo("gato")
        assertThat(cat.isUnpaired).isFalse()
    }

    @Test
    fun parseWithLayout_ignoreZoneOverTitle_pairsCorrectly() {
        val lines = listOf(
            line("Chapter 1", 0.05f, 0.10f, 0.25f, 0.13f),
            line("cat", 0.05f, 0.18f, 0.30f, 0.21f),
            line("dog", 0.05f, 0.26f, 0.30f, 0.29f),
            line("gato", 0.55f, 0.10f, 0.80f, 0.13f),
            line("perro", 0.55f, 0.18f, 0.80f, 0.21f),
        )
        val zone = ZoneRect(left = 0.0f, top = 0.08f, right = 0.4f, bottom = 0.16f)
        val rows = ColumnParser.parseWithLayout(lines, splitX = 0.30f, ignoreZones = listOf(zone))
        assertThat(rows).hasSize(2)
        assertThat(rows[0].front).isEqualTo("cat")
        assertThat(rows[0].back).isEqualTo("gato")
        assertThat(rows[1].front).isEqualTo("dog")
        assertThat(rows[1].back).isEqualTo("perro")
        assertThat(rows[0].isUnpaired).isFalse()
    }

    @Test
    fun parseWithLayout_lineInsideZone_isExcludedEntirely() {
        val lines = listOf(
            line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
            line("gato", 0.55f, 0.10f, 0.80f, 0.13f),
            line("page 3", 0.55f, 0.26f, 0.80f, 0.29f),
        )
        val zone = ZoneRect(left = 0.45f, top = 0.24f, right = 0.9f, bottom = 0.32f)
        val rows = ColumnParser.parseWithLayout(lines, splitX = 0.30f, ignoreZones = listOf(zone))
        assertThat(rows).hasSize(1)
        assertThat(rows[0].front).isEqualTo("cat")
        assertThat(rows[0].back).isEqualTo("gato")
        assertThat(rows[0].isUnpaired).isFalse()
    }

    @Test
    fun parseWithLayout_customSplitX_overridesAutoDetection() {
        val lines = listOf(
            line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
            line("gato", 0.31f, 0.10f, 0.56f, 0.13f),
        )
        val rows = ColumnParser.parseWithLayout(lines, splitX = 0.30f, ignoreZones = emptyList())
        assertThat(rows).hasSize(1)
        assertThat(rows[0].front).isEqualTo("cat")
        assertThat(rows[0].back).isEqualTo("gato")
        assertThat(rows[0].isUnpaired).isFalse()
    }

    @Test
    fun pairLines_matchesByIndexUpToShorterList() {
        val left = listOf(
            line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
            line("dog", 0.05f, 0.18f, 0.30f, 0.21f),
        )
        val right = listOf(
            line("gato", 0.55f, 0.10f, 0.80f, 0.13f),
        )
        val pairs = ColumnParser.pairLines(left, right)
        assertThat(pairs).hasSize(1)
        assertThat(pairs[0].first.text).isEqualTo("cat")
        assertThat(pairs[0].second.text).isEqualTo("gato")
    }

    @Test
    fun pairLines_emptySide_returnsEmpty() {
        val left = listOf(line("cat", 0.05f, 0.10f, 0.30f, 0.13f))
        assertThat(ColumnParser.pairLines(left, emptyList())).isEmpty()
        assertThat(ColumnParser.pairLines(emptyList(), left)).isEmpty()
    }

    @Test
    fun parseWithLayout_nullSplit_treatsAllAsUnpaired() {
        val lines = listOf(
            line("cat", 0.05f, 0.10f, 0.30f, 0.13f),
            line("gato", 0.55f, 0.10f, 0.80f, 0.13f),
        )
        val rows = ColumnParser.parseWithLayout(lines, splitX = null, ignoreZones = emptyList())
        assertThat(rows).hasSize(2)
        assertThat(rows[0].isUnpaired).isTrue()
        assertThat(rows[1].isUnpaired).isTrue()
    }
}
