package com.scan2anki.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scan2anki.R
import com.scan2anki.data.Page
import com.scan2anki.data.SessionRepository
import com.scan2anki.data.WordPairMapper
import com.scan2anki.ocr.OcrEngine
import com.scan2anki.parse.ColumnParser
import com.scan2anki.util.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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

data class CaptureUiState(
    val sessionId: Long = 0L,
    val pages: List<Page> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val repo: SessionRepository,
    @Named("onDevice") private val onDeviceOcr: OcrEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun init(sessionId: Long) {
        Timber.d("CaptureViewModel.init for session=%d", sessionId)
        _uiState.update { it.copy(sessionId = sessionId) }
        viewModelScope.launch {
            repo.pages(sessionId).collect { pages ->
                Timber.v("CaptureViewModel received %d page(s)", pages.size)
                _uiState.update { it.copy(pages = pages) }
            }
        }
    }

    fun addPageFromUri(uri: Uri) {
        val sessionId = _uiState.value.sessionId
        if (sessionId == 0L || _uiState.value.isProcessing) {
            Timber.d("Ignoring addPageFromUri (sessionId=%d, isProcessing=%b)", sessionId, _uiState.value.isProcessing)
            return
        }
        Timber.i("CaptureViewModel adding page from uri=%s to session=%d", uri, sessionId)
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            var pageId: Long? = null
            try {
                val order = _uiState.value.pages.size
                val dir = File(context.filesDir, "pages/$sessionId").apply { mkdirs() }
                val dest = File(dir, "page_$order.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException(context.getString(R.string.error_cannot_read_image))
                Timber.d("Copied image to %s (%d bytes)", dest.absolutePath, dest.length())
                val insertedPageId = repo.addPage(sessionId, dest.absolutePath, order)
                pageId = insertedPageId
                val bitmap = ImageUtils.decodeRotated(dest.absolutePath)
                    ?: throw IOException(context.getString(R.string.error_cannot_decode_image))
                Timber.d("Decoded %dx%d bitmap for page=%d", bitmap.width, bitmap.height, insertedPageId)
                val result = onDeviceOcr.recognize(bitmap)
                Timber.i("OCR for page=%d produced %d line(s)", insertedPageId, result.lines.size)
                val rows = ColumnParser.parse(result)
                val pairs = rows.mapIndexed { index, row ->
                    WordPairMapper.fromRow(sessionId, insertedPageId, index, row)
                }
                repo.replaceWordPairsForPage(sessionId, insertedPageId, pairs)
                repo.updatePageOcrState(insertedPageId, "DONE")
                Timber.i("Page=%d processed with %d word pair(s)", insertedPageId, pairs.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to process page from uri=%s", uri)
                pageId?.let { repo.updatePageOcrState(it, "FAILED") }
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.error_ocr_failed)) }
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun doneAddingPages() {
        Timber.i("CaptureViewModel marking capture done for session=%d", _uiState.value.sessionId)
        _uiState.update { it.copy(done = true) }
    }

    fun deletePage(pageId: Long) {
        Timber.d("CaptureViewModel.deletePage id=%d", pageId)
        viewModelScope.launch {
            val page = _uiState.value.pages.firstOrNull { it.id == pageId } ?: return@launch
            repo.deletePage(page)
        }
    }

    fun consumeDone() {
        Timber.v("CaptureViewModel consuming done flag")
        _uiState.update { it.copy(done = false) }
    }

    fun consumeError() {
        Timber.v("CaptureViewModel consuming error=%s", _uiState.value.error)
        _uiState.update { it.copy(error = null) }
    }

    fun reportError(message: String) {
        Timber.w("CaptureViewModel reports error: %s", message)
        _uiState.update { it.copy(error = message) }
    }
}