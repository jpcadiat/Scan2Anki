package com.scan2anki.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scan2anki.R
import com.scan2anki.ankidroid.AnkiDroidSendResult
import com.scan2anki.ankidroid.AnkiDroidSender
import com.scan2anki.ankidroid.AnkiNote
import com.scan2anki.data.Page
import com.scan2anki.data.SessionRepository
import com.scan2anki.data.WordPair
import com.scan2anki.data.WordPairMapper
import com.scan2anki.ocr.OcrEngine
import com.scan2anki.parse.OcrCleanup
import com.scan2anki.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class SendResult { SENT }

data class ReviewUiState(
    val sessionId: Long = 0L,
    val deckName: String = "",
    val pairs: List<WordPair> = emptyList(),
    val pages: List<Page> = emptyList(),
    val isProcessing: Boolean = false,
    val sendResult: SendResult? = null,
    val error: String? = null,
    val cleanupConfig: OcrCleanup.CleanupConfig = OcrCleanup.CleanupConfig(),
    val deckNames: List<String> = emptyList(),
    val isLoadingDeckNames: Boolean = false,
    val showDeckPicker: Boolean = false,
    val noteTypeName: String = "",
    val noteTypeNames: List<String> = emptyList(),
    val isLoadingNoteTypeNames: Boolean = false,
    val showNoteTypePicker: Boolean = false,
    val cloudOcrAvailable: Boolean = false,
)
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repo: SessionRepository,
    @Named("onDevice") private val onDeviceOcr: OcrEngine,
    private val ankiSender: AnkiDroidSender,
    private val settings: AppSettings,
    @ApplicationContext private val context: Context,
) : ViewModel() {


    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    fun init(sessionId: Long) {
        Timber.d("ReviewViewModel.init for session=%d", sessionId)
        viewModelScope.launch {
            _uiState.update { it.copy(sessionId = sessionId) }
            val defaultDeck = settings.defaultDeckName.first()
            Timber.d("Loaded default deck name \"%s\"", defaultDeck)
            val storedCleanupConfig = settings.cleanupConfig.first()
            val storedNoteType = settings.defaultNoteType.first()
            val effectiveNoteType = storedNoteType.ifBlank { context.getString(R.string.default_note_type_name) }
            Timber.d("Loaded effective default note type \"%s\"", effectiveNoteType)
            _uiState.update {
                it.copy(
                    deckName = defaultDeck,
                    cleanupConfig = storedCleanupConfig,
                    noteTypeName = effectiveNoteType,
                )
            }
            repo.wordPairs(sessionId).collect { pairs ->
                Timber.v("ReviewViewModel received %d word pair(s)", pairs.size)
                _uiState.update { it.copy(pairs = pairs) }
            }
        }
        viewModelScope.launch {
            repo.pages(sessionId).collect { pages ->
                Timber.v("ReviewViewModel received %d page(s)", pages.size)
                _uiState.update { it.copy(pages = pages) }
            }
        }
        viewModelScope.launch {
            settings.cloudOcrApiKey.collect { key ->
                Timber.v("ReviewViewModel: cloud OCR availability changed, available=%b", key.isNotBlank())
                _uiState.update { it.copy(cloudOcrAvailable = key.isNotBlank()) }
            }
        }
    }
    fun updateFront(id: Long, text: String) {
        Timber.v("ReviewViewModel.updateFront id=%d", id)
        // Reflected in _uiState synchronously so the field shows what was just typed
        // right away, instead of waiting on the DB write + Room Flow round trip: any
        // recomposition in that window would otherwise redisplay the stale value and
        // reset the text field's cursor to the end.
        val pair = updatePairInState(id) { it.copy(front = text) } ?: run {
            Timber.w("updateFront: pair id=%d not found", id)
            return
        }
        viewModelScope.launch { repo.updateWordPair(pair) }
    }

    fun updateBack(id: Long, text: String) {
        Timber.v("ReviewViewModel.updateBack id=%d", id)
        val pair = updatePairInState(id) { it.copy(back = text) } ?: run {
            Timber.w("updateBack: pair id=%d not found", id)
            return
        }
        viewModelScope.launch { repo.updateWordPair(pair) }
    }

    /** Applies [transform] to the pair with [id] in [_uiState], returning the updated pair. */
    private fun updatePairInState(id: Long, transform: (WordPair) -> WordPair): WordPair? {
        var updated: WordPair? = null
        _uiState.update { state ->
            state.copy(
                pairs = state.pairs.map { pair ->
                    if (pair.id == id) transform(pair).also { updated = it } else pair
                },
            )
        }
        return updated
    }

    fun deletePair(id: Long) {
        Timber.d("ReviewViewModel.deletePair id=%d", id)
        viewModelScope.launch { repo.deleteWordPair(id) }
    }

    /**
     * Re-inserts a deleted pair, keeping its original id and order so an Undo restores the row
     * exactly where it was. This is what lets deletion be immediate instead of confirmed.
     */
    fun restorePair(pair: WordPair) {
        Timber.d("ReviewViewModel.restorePair id=%d", pair.id)
        viewModelScope.launch { repo.addWordPair(pair) }
    }

    fun deletePage(pageId: Long) {
        Timber.d("ReviewViewModel.deletePage id=%d", pageId)
        viewModelScope.launch {
            val page = _uiState.value.pages.firstOrNull { it.id == pageId } ?: return@launch
            repo.deletePage(page)
        }
    }

    fun addPair() {
        val state = _uiState.value
        Timber.d("ReviewViewModel.addPair for session=%d", state.sessionId)
        viewModelScope.launch {
            val order = (state.pairs.maxOfOrNull { it.order } ?: -1) + 1
            repo.addWordPair(
                WordPair(sessionId = state.sessionId, front = "", back = "", order = order),
            )
        }
    }

    fun swapColumns() {
        val state = _uiState.value
        Timber.i("ReviewViewModel.swapColumns for %d pair(s)", state.pairs.size)
        viewModelScope.launch {
            state.pairs.forEach { pair ->
                repo.updateWordPair(pair.copy(front = pair.back, back = pair.front))
            }
        }
    }

    fun setDeckName(name: String) {
        Timber.v("ReviewViewModel.setDeckName to \"%s\"", name)
        _uiState.update { it.copy(deckName = name) }
    }

    fun openDeckPicker() {
        Timber.d("ReviewViewModel: opening deck picker")
        _uiState.update { it.copy(showDeckPicker = true, isLoadingDeckNames = true) }
        viewModelScope.launch {
            val names = withContext(Dispatchers.IO) { ankiSender.getDeckNames() }
            Timber.d("ReviewViewModel: loaded %d deck name(s)", names.size)
            _uiState.update { it.copy(deckNames = names, isLoadingDeckNames = false) }
        }
    }

    fun dismissDeckPicker() {
        _uiState.update { it.copy(showDeckPicker = false) }
    }

    fun setNoteType(name: String) {
        Timber.d("ReviewViewModel.setNoteType to \"%s\"", name)
        _uiState.update { it.copy(noteTypeName = name) }
        viewModelScope.launch { settings.setDefaultNoteType(name) }
    }

    fun openNoteTypePicker() {
        Timber.d("ReviewViewModel: opening note type picker")
        _uiState.update { it.copy(showNoteTypePicker = true, isLoadingNoteTypeNames = true) }
        viewModelScope.launch {
            val names = withContext(Dispatchers.IO) { ankiSender.getNoteTypeNames() }
            Timber.d("ReviewViewModel: loaded %d note type name(s)", names.size)
            _uiState.update { it.copy(noteTypeNames = names, isLoadingNoteTypeNames = false) }
        }
    }

    fun dismissNoteTypePicker() {
        _uiState.update { it.copy(showNoteTypePicker = false) }
    }

    fun sendToAnkiDroid() {
        val state = _uiState.value
        if (state.deckName.isBlank()) {
            Timber.w("sendToAnkiDroid: deck name is blank")
            _uiState.update { it.copy(error = context.getString(R.string.error_enter_deck_name)) }
            return
        }
        val deck = state.deckName
        val noteType = state.noteTypeName
        val notes = state.pairs.filter {
            it.front.isNotBlank() && it.back.isNotBlank() && !it.isHeader
        }.map { AnkiNote(it.front, it.back) }
        if (notes.isEmpty()) {
            Timber.w("sendToAnkiDroid: no valid word pairs to export")
            _uiState.update { it.copy(error = context.getString(R.string.error_no_valid_pairs)) }
            return
        }
        Timber.i("sendToAnkiDroid: sending %d card(s) to deck \"%s\" as note type \"%s\"", notes.size, deck, noteType)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ankiSender.sendCards(deck, noteType, notes)
            }
            when (result) {
                is AnkiDroidSendResult.Added -> {
                    if (result.added > 0) {
                        repo.setDeckName(state.sessionId, deck)
                        repo.markImported(state.sessionId)
                    }
                    if (result.added == result.total) {
                        Timber.i("All %d card(s) sent to AnkiDroid", notes.size)
                        _uiState.update { it.copy(sendResult = SendResult.SENT) }
                    } else {
                        Timber.w("Only %d of %d card(s) could be added", result.added, result.total)
                        _uiState.update {
                            it.copy(
                                error = context.getString(
                                    R.string.msg_cards_partial_added,
                                    result.added,
                                    result.total,
                                ),
                            )
                        }
                    }
                }
                is AnkiDroidSendResult.Failed -> {
                    Timber.e("sendToAnkiDroid failed: %s", result.reason)
                    _uiState.update { it.copy(error = result.reason) }
                }
            }
        }
    }

    fun previewCleanup(config: OcrCleanup.CleanupConfig): List<Pair<WordPair, WordPair>> =
        OcrCleanup.preview(_uiState.value.pairs, config)

    fun applyCleanup(config: OcrCleanup.CleanupConfig) {
        val changed = OcrCleanup.preview(_uiState.value.pairs, config)
        Timber.i("applyCleanup: updating %d changed pair(s)", changed.size)
        viewModelScope.launch {
            changed.forEach { (_, updated) -> repo.updateWordPair(updated) }
        }
    }

    fun setCleanupConfig(config: OcrCleanup.CleanupConfig) {
        _uiState.update { it.copy(cleanupConfig = config) }
        viewModelScope.launch { settings.setCleanupConfig(config) }
    }

    fun consumeSendResult() {
        Timber.v("ReviewViewModel consuming sendResult=%s", _uiState.value.sendResult)
        _uiState.update { it.copy(sendResult = null) }
    }

    fun consumeError() {
        Timber.v("ReviewViewModel consuming error=%s", _uiState.value.error)
        _uiState.update { it.copy(error = null) }
    }
}