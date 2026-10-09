package com.scan2anki.parse

import com.scan2anki.data.WordPair
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

object OcrCleanup {

    @Serializable
    enum class ColumnScope { FRONT, BACK, BOTH }

    @Serializable
    enum class CasingMode { SENTENCE, LOWERCASE }

    @Serializable
    data class TrimJunkRule(
        val enabled: Boolean = false,
        val scope: ColumnScope = ColumnScope.BOTH,
        val extraChars: String = "",
    )

    @Serializable
    data class PageNumberRule(
        val enabled: Boolean = false,
        val scope: ColumnScope = ColumnScope.BOTH,
    )

    @Serializable
    data class SeparatorCutRule(
        val enabled: Boolean = false,
        val scope: ColumnScope = ColumnScope.BOTH,
        val separator: String = "",
    )

    @Serializable
    data class CasingRule(
        val enabled: Boolean = false,
        val scope: ColumnScope = ColumnScope.BOTH,
        val mode: CasingMode = CasingMode.SENTENCE,
    )

    @Serializable
    data class CleanupConfig(
        val trimJunk: TrimJunkRule = TrimJunkRule(),
        val pageNumber: PageNumberRule = PageNumberRule(),
        val separatorCut: SeparatorCutRule = SeparatorCutRule(),
        val casing: CasingRule = CasingRule(),
    )

    // Separator punctuation only — deliberately excludes closing brackets/quotes
    // ()[]{}"'` so a trailing page number doesn't eat the closing punctuation
    // of a preceding comment, e.g. "apple (fruit) 12" -> "apple (fruit)".
    // The group repeats to also strip comma-separated page lists, e.g. "2, 5".
    private val TRAILING_PAGE_NUMBER = Regex("""(?:[\s!#$%&*+,./:;<=>?@^_|~-]+\d+)+\s*$""")

    fun trimJunk(text: String, extraChars: String): String {
        val extra = extraChars.toSet()
        return text.trim { c -> !c.isLetterOrDigit() || c in extra }
    }

    fun stripTrailingPageNumber(text: String): String =
        text.replace(TRAILING_PAGE_NUMBER, "")

    fun cutAfterSeparator(text: String, separator: String): String {
        if (separator.isEmpty()) return text
        val idx = text.indexOf(separator)
        return if (idx == -1) text else text.substring(0, idx).trimEnd()
    }

    fun fixCasing(text: String, mode: CasingMode): String = when (mode) {
        CasingMode.LOWERCASE -> text.lowercase()
        CasingMode.SENTENCE -> text.lowercase().replaceFirstChar { it.uppercaseChar() }
    }

    fun apply(pair: WordPair, config: CleanupConfig): WordPair =
        pair.copy(
            front = cleanCell(pair.front, config, isFront = true),
            back = cleanCell(pair.back, config, isFront = false),
        )

    fun preview(pairs: List<WordPair>, config: CleanupConfig): List<Pair<WordPair, WordPair>> =
        pairs.map { it to apply(it, config) }
            .filter { (old, new) -> old.front != new.front || old.back != new.back }

    private fun ColumnScope.appliesTo(isFront: Boolean): Boolean = when (this) {
        ColumnScope.BOTH -> true
        ColumnScope.FRONT -> isFront
        ColumnScope.BACK -> !isFront
    }

    private fun cleanCell(text: String, config: CleanupConfig, isFront: Boolean): String {
        var result = text
        if (config.trimJunk.enabled && config.trimJunk.scope.appliesTo(isFront)) {
            result = trimJunk(result, config.trimJunk.extraChars)
        }
        if (config.pageNumber.enabled && config.pageNumber.scope.appliesTo(isFront)) {
            result = stripTrailingPageNumber(result)
        }
        if (config.separatorCut.enabled && config.separatorCut.scope.appliesTo(isFront)) {
            result = cutAfterSeparator(result, config.separatorCut.separator)
        }
        if (config.casing.enabled && config.casing.scope.appliesTo(isFront)) {
            result = fixCasing(result, config.casing.mode)
        }
        return result.trim()
    }
}

object CleanupConfigCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(config: OcrCleanup.CleanupConfig): String =
        json.encodeToString(OcrCleanup.CleanupConfig.serializer(), config)

    fun decode(raw: String?): OcrCleanup.CleanupConfig {
        if (raw.isNullOrBlank()) return OcrCleanup.CleanupConfig()
        return try {
            json.decodeFromString(OcrCleanup.CleanupConfig.serializer(), raw)
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization.SerializationException extends IllegalArgumentException,
            // so this single catch also covers malformed-JSON/schema-mismatch failures.
            Timber.w(e, "Failed to decode cached cleanup config")
            OcrCleanup.CleanupConfig()
        }
    }
}
