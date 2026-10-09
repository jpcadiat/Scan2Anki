package com.scan2anki.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scan2anki.R
import com.scan2anki.data.Page
import com.scan2anki.data.PageZoneCache
import com.scan2anki.data.PageZoneCacheCodec
import com.scan2anki.data.SessionRepository
import com.scan2anki.data.WordPairMapper
import com.scan2anki.ocr.OcrEngine
import com.scan2anki.ocr.OcrLine
import com.scan2anki.parse.ColumnParser
import com.scan2anki.parse.ColumnParser.ZoneRect
import com.scan2anki.util.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class PageZoneLayout(
    val pageId: Long,
    val splitX: Float?,
    val ignoreZones: List<ZoneRect>,
)

data class EditorPage(
    val page: Page,
    val imageWidth: Int,
    val imageHeight: Int,
    val lines: List<OcrLine>,
    val deletedLines: List<Int> = emptyList(),
    val layout: PageZoneLayout,
    val isLoaded: Boolean = false,
    val loadedFromCache: Boolean = false,
) {
    val visibleLines: List<OcrLine>
        get() = lines.filterIndexed { index, _ -> index !in deletedLines }
}

/**
 * Everything a single page edit can change, captured so a snackbar can offer Undo instead of
 * the editor asking for confirmation up front.
 */
data class PageEditSnapshot(
    val pageId: Long,
    val splitX: Float?,
    val ignoreZones: List<ZoneRect>,
    val deletedLines: List<Int>,
)

data class ZoneEditorUiState(
    val sessionId: Long = 0L,
    val pages: List<EditorPage> = emptyList(),
    val currentIndex: Int = 0,
    val preview: List<ColumnParser.ParsedRow> = emptyList(),
    val done: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val focusedPageId: Long? = null,
    val useCloud: Boolean = false,
    val restoreFromCache: Boolean = false,
)

@HiltViewModel
class ZoneEditorViewModel @Inject constructor(
    private val repo: SessionRepository,
    @Named("onDevice") private val onDeviceOcr: OcrEngine,
    @Named("cloud") private val cloudOcr: OcrEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZoneEditorUiState())
    val uiState: StateFlow<ZoneEditorUiState> = _uiState.asStateFlow()

    fun init(
        sessionId: Long,
        focusedPageId: Long? = null,
        useCloud: Boolean = false,
        restoreFromCache: Boolean = false,
    ) {
        Timber.d(
            "ZoneEditorViewModel.init session=%d focusedPage=%s useCloud=%b restoreFromCache=%b",
            sessionId, focusedPageId, useCloud, restoreFromCache,
        )
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sessionId = sessionId,
                    focusedPageId = focusedPageId,
                    useCloud = useCloud,
                    restoreFromCache = restoreFromCache,
                )
            }
            repo.pages(sessionId).collect { pages ->
                Timber.v("ZoneEditorViewModel received %d page(s)", pages.size)
                val existing = _uiState.value.pages.associateBy { it.page.id }
                _uiState.update { state ->
                    val focusedIdx = focusedPageId?.let { id -> pages.indexOfFirst { it.id == id } } ?: -1
                    val newIndex = if (pages.isEmpty()) {
                        state.currentIndex
                    } else if (focusedIdx >= 0) {
                        focusedIdx
                    } else {
                        state.currentIndex.coerceIn(0, pages.lastIndex)
                    }
                    state.copy(
                        pages = pages.map { page ->
                            existing[page.id]?.let { it.copy(page = page) }
                                ?: EditorPage(
                                    page = page,
                                    imageWidth = 0,
                                    imageHeight = 0,
                                    lines = emptyList(),
                                    layout = PageZoneLayout(page.id, null, emptyList()),
                                )
                        },
                        currentIndex = newIndex,
                    )
                }
                ensureCurrentPageLoaded()
            }
        }
    }

    fun setCurrentPage(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.pages.size) {
            Timber.v("setCurrentPage ignored for index=%d", index)
            return
        }
        Timber.d("ZoneEditorViewModel switching to page index=%d", index)
        _uiState.update { it.copy(currentIndex = index, preview = computePreview(it.pages, index)) }
        ensureCurrentPageLoaded()
    }

    fun setSplitX(x: Float) {
        val clamped = x.coerceIn(0f, 1f)
        Timber.v("ZoneEditorViewModel.setSplitX %.4f", clamped)
        updateCurrentLayout { it.copy(splitX = clamped) }
    }

    fun removeSplit() {
        Timber.d("ZoneEditorViewModel.removeSplit")
        updateCurrentLayout { it.copy(splitX = null) }
    }

    fun addSplit(position: Float) {
        val clamped = position.coerceIn(0f, 1f)
        Timber.d("ZoneEditorViewModel.addSplit %.4f", clamped)
        updateCurrentLayout { it.copy(splitX = clamped) }
    }

    fun addIgnoreZone(zone: ZoneRect) {
        Timber.d("ZoneEditorViewModel.addIgnoreZone %s", zone)
        updateCurrentLayout { it.copy(ignoreZones = it.ignoreZones + zone) }
    }

    fun removeIgnoreZone(index: Int) {
        Timber.d("ZoneEditorViewModel.removeIgnoreZone index=%d", index)
        updateCurrentLayout { it.copy(ignoreZones = it.ignoreZones.filterIndexed { i, _ -> i != index }) }
    }

    fun deleteLine(index: Int) {
        Timber.d("ZoneEditorViewModel.deleteLine index=%d", index)
        updateCurrentPage { page ->
            if (index !in page.lines.indices || index in page.deletedLines) page
            else page.copy(deletedLines = (page.deletedLines + index).sorted())
        }
    }

    fun updateIgnoreZone(index: Int, rect: ZoneRect) {
        Timber.d("ZoneEditorViewModel.updateIgnoreZone index=%d rect=%s", index, rect)
        updateCurrentPage { page ->
            if (index !in page.layout.ignoreZones.indices) page
            else page.copy(
                layout = page.layout.copy(
                    ignoreZones = page.layout.ignoreZones.toMutableList().also { it[index] = rect },
                ),
            )
        }
    }

    fun resetPage() {
        Timber.d("ZoneEditorViewModel.resetPage")
        updateCurrentPage { page ->
            page.copy(
                deletedLines = emptyList(),
                layout = page.layout.copy(
                    splitX = ColumnParser.detectColumnSplit(page.visibleLines),
                    ignoreZones = emptyList(),
                ),
            )
        }
    }

    /** Captures the current page's editable state for a later [restore]. */
    fun snapshotCurrentPage(): PageEditSnapshot? {
        val state = _uiState.value
        val page = state.pages.getOrNull(state.currentIndex) ?: return null
        return PageEditSnapshot(
            pageId = page.page.id,
            splitX = page.layout.splitX,
            ignoreZones = page.layout.ignoreZones,
            deletedLines = page.deletedLines,
        )
    }

    /** Restores a [snapshotCurrentPage] result, undoing whatever happened since. */
    fun restore(snapshot: PageEditSnapshot) {
        Timber.i("ZoneEditorViewModel.restore page=%d", snapshot.pageId)
        _uiState.update { s ->
            val pages = s.pages.toMutableList()
            val idx = pages.indexOfFirst { it.page.id == snapshot.pageId }
            if (idx < 0) return@update s
            pages[idx] = pages[idx].copy(
                deletedLines = snapshot.deletedLines,
                layout = pages[idx].layout.copy(
                    splitX = snapshot.splitX,
                    ignoreZones = snapshot.ignoreZones,
                ),
            )
            s.copy(pages = pages, preview = computePreview(pages, s.currentIndex))
        }
    }

    fun clearZones() {
        Timber.d("ZoneEditorViewModel.clearZones")
        updateCurrentLayout { it.copy(ignoreZones = emptyList()) }
    }

    fun done() {
        val state = _uiState.value
        if (state.sessionId == 0L || state.isProcessing) {
            Timber.d("Ignoring done (sessionId=%d, isProcessing=%b)", state.sessionId, state.isProcessing)
            return
        }
        Timber.i("ZoneEditorViewModel.done for session=%d focusedPage=%s", state.sessionId, state.focusedPageId)
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val sessionId = _uiState.value.sessionId
                val focused = _uiState.value.focusedPageId
                val targets = if (focused != null) {
                    _uiState.value.pages.filter { it.page.id == focused }
                } else {
                    _uiState.value.pages
                }
                val loaded = targets.map { loadPage(it.page.id) }
                loaded.forEach { editorPage ->
                    val rows = ColumnParser.parseWithLayout(editorPage.visibleLines, editorPage.layout.splitX, editorPage.layout.ignoreZones)
                    val pairs = rows.mapIndexed { index, row ->
                        WordPairMapper.fromRow(sessionId, editorPage.page.id, index, row)
                    }
                    repo.replaceWordPairsForPage(sessionId, editorPage.page.id, pairs)
                    repo.updatePageZoneCache(
                        editorPage.page.id,
                        PageZoneCache(
                            imageWidth = editorPage.imageWidth,
                            imageHeight = editorPage.imageHeight,
                            lines = editorPage.lines,
                            splitX = editorPage.layout.splitX,
                            ignoreZones = editorPage.layout.ignoreZones,
                            deletedLines = editorPage.deletedLines,
                        ),
                    )
                    if (!editorPage.loadedFromCache) {
                        repo.updatePageOcrState(
                            editorPage.page.id,
                            if (_uiState.value.useCloud) "DONE_CLOUD" else "DONE",
                        )
                    }
                    Timber.i("Zone editor stored %d pair(s) for page=%d", pairs.size, editorPage.page.id)
                }
                _uiState.update { it.copy(done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Zone editor done() failed for session=%d", state.sessionId)
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.error_ocr_failed)) }
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun consumeError() {
        Timber.v("ZoneEditorViewModel consuming error=%s", _uiState.value.error)
        _uiState.update { it.copy(error = null) }
    }

    fun retryCurrentPage() {
        Timber.d("ZoneEditorViewModel.retryCurrentPage index=%d", _uiState.value.currentIndex)
        ensureCurrentPageLoaded()
    }

    private fun ensureCurrentPageLoaded() {
        val state = _uiState.value
        val page = state.pages.getOrNull(state.currentIndex) ?: return
        if (page.isLoaded) return
        processPage(page.page.id)
    }

    private fun processPage(pageId: Long) {
        viewModelScope.launch {
            if (_uiState.value.isProcessing) {
                Timber.v("Skipping processPage=%d while processing", pageId)
                return@launch
            }
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val loaded = loadPage(pageId)
                _uiState.update { s ->
                    val pages = s.pages.toMutableList()
                    val idx = pages.indexOfFirst { it.page.id == pageId }
                    if (idx >= 0) pages[idx] = loaded
                    s.copy(pages = pages, preview = computePreview(pages, s.currentIndex))
                }
                ensureCurrentPageLoaded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load page=%d in zone editor", pageId)
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.error_ocr_failed)) }
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private suspend fun loadPage(pageId: Long): EditorPage {
        val state = _uiState.value
        val idx = state.pages.indexOfFirst { it.page.id == pageId }
        val editorPage = state.pages.getOrNull(idx)
            ?: throw IOException(context.getString(R.string.error_page_not_found))
        if (editorPage.isLoaded) return editorPage
        if (state.restoreFromCache) {
            val cache = PageZoneCacheCodec.decode(editorPage.page.zoneCacheJson)
            if (cache != null) {
                Timber.i(
                    "Zone editor restored cached layout for page=%d (%d line(s))",
                    pageId, cache.lines.size,
                )
                return editorPage.copy(
                    imageWidth = cache.imageWidth,
                    imageHeight = cache.imageHeight,
                    lines = cache.lines,
                    deletedLines = cache.deletedLines,
                    layout = PageZoneLayout(pageId, cache.splitX, cache.ignoreZones),
                    isLoaded = true,
                    loadedFromCache = true,
                )
            }
            Timber.w("No cached zone layout for page=%d; falling back to fresh OCR", pageId)
        }
        val bitmap = ImageUtils.decodeRotated(editorPage.page.imagePath)
            ?: throw IOException(context.getString(R.string.error_cannot_decode_image))
        val engine = if (state.useCloud) cloudOcr else onDeviceOcr
        val result = engine.recognize(bitmap)
        val splitX = ColumnParser.detectColumnSplit(result.lines)
        Timber.i(
            "Zone editor loaded page=%d with %d line(s), split=%.4f engine=%s",
            pageId, result.lines.size, splitX ?: -1f, if (state.useCloud) "cloud" else "on-device",
        )
        return editorPage.copy(
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            lines = result.lines,
            layout = editorPage.layout.copy(splitX = splitX),
            isLoaded = true,
            loadedFromCache = false,
        )
    }

    private fun updateCurrentPage(transform: (EditorPage) -> EditorPage) {
        val state = _uiState.value
        val idx = state.currentIndex
        if (state.pages.getOrNull(idx) == null) return
        _uiState.update { s ->
            val pages = s.pages.toMutableList()
            pages[idx] = transform(pages[idx])
            s.copy(pages = pages, preview = computePreview(pages, idx))
        }
    }

    private fun updateCurrentLayout(transform: (PageZoneLayout) -> PageZoneLayout) =
        updateCurrentPage { it.copy(layout = transform(it.layout)) }

    private fun computePreview(pages: List<EditorPage>, index: Int): List<ColumnParser.ParsedRow> {
        val page = pages.getOrNull(index) ?: return emptyList()
        if (page.visibleLines.isEmpty()) return emptyList()
        return ColumnParser.parseWithLayout(page.visibleLines, page.layout.splitX, page.layout.ignoreZones)
    }
}
