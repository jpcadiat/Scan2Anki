package com.scan2anki.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scan2anki.R
import com.scan2anki.ankidroid.AnkiDroidSender
import com.scan2anki.ocr.CloudVisionOcrEngine
import com.scan2anki.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class AnkiDroidTestResult(
    val ok: Boolean,
    val message: String,
)

private data class SettingsSnapshot(
    val apiKey: String,
    val defaultDeckName: String,
    val ocrScript: String,
    val defaultNoteType: String,
)

data class SettingsUiState(
    val apiKey: String = "",
    val defaultDeckName: String = "",
    val defaultNoteType: String = "",
    val ocrScript: String = "latin",
    val isTestingAnkiDroid: Boolean = false,
    val ankiDroidTestResult: AnkiDroidTestResult? = null,
    val isTestingApiKey: Boolean = false,
    val apiKeyTestResult: AnkiDroidTestResult? = null,
    val deckNames: List<String> = emptyList(),
    val isLoadingDeckNames: Boolean = false,
    val showDeckPicker: Boolean = false,
    val noteTypeNames: List<String> = emptyList(),
    val isLoadingNoteTypeNames: Boolean = false,
    val showNoteTypePicker: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val ankiSender: AnkiDroidSender,
    private val cloudOcr: CloudVisionOcrEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun init() {
        Timber.d("SettingsViewModel.init: collecting settings")
        viewModelScope.launch {
            combine(
                settings.apiKey,
                settings.defaultDeckName,
                settings.ocrScript,
                settings.defaultNoteType,
            ) { apiKey, defaultDeckName, ocrScript, defaultNoteType ->
                SettingsSnapshot(apiKey, defaultDeckName, ocrScript, defaultNoteType)
            }.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        apiKey = snapshot.apiKey,
                        defaultDeckName = snapshot.defaultDeckName,
                        ocrScript = snapshot.ocrScript,
                        defaultNoteType = snapshot.defaultNoteType,
                    )
                }
            }
        }
    }

    fun setApiKey(value: String) {
        Timber.i("SettingsViewModel.setApiKey (configured=%b)", value.isNotBlank())
        _uiState.update { it.copy(apiKey = value) }
        viewModelScope.launch { settings.setApiKey(value) }
    }

    fun setDefaultDeckName(value: String) {
        Timber.d("SettingsViewModel.setDefaultDeckName to \"%s\"", value)
        _uiState.update { it.copy(defaultDeckName = value) }
        viewModelScope.launch { settings.setDefaultDeckName(value) }
    }

    fun setDefaultNoteType(value: String) {
        Timber.d("SettingsViewModel.setDefaultNoteType to \"%s\"", value)
        _uiState.update { it.copy(defaultNoteType = value) }
        viewModelScope.launch { settings.setDefaultNoteType(value) }
    }

    fun setOcrScript(value: String) {
        Timber.d("SettingsViewModel.setOcrScript to \"%s\"", value)
        _uiState.update { it.copy(ocrScript = value) }
        viewModelScope.launch { settings.setOcrScript(value) }
    }

    fun testAnkiDroid() {
        Timber.i("SettingsViewModel: testing AnkiDroid connection")
        _uiState.update { it.copy(isTestingAnkiDroid = true) }
        viewModelScope.launch {
            try {
                val diagnosis = withContext(Dispatchers.IO) {
                    ankiSender.diagnose()
                }
                Timber.i("SettingsViewModel: diagnosis ok=%b, msg=%s", diagnosis.ok, diagnosis.message)
                _uiState.update {
                    it.copy(
                        isTestingAnkiDroid = false,
                        ankiDroidTestResult = AnkiDroidTestResult(
                            ok = diagnosis.ok,
                            message = diagnosis.message,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "SettingsViewModel: diagnosis failed")
                _uiState.update {
                    it.copy(
                        isTestingAnkiDroid = false,
                        ankiDroidTestResult = AnkiDroidTestResult(
                            ok = false,
                            message = context.getString(
                                R.string.error_test_failed,
                                e.message ?: context.getString(R.string.error_unknown),
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun testApiKey() {
        Timber.i("SettingsViewModel: testing Cloud Vision API key")
        if (_uiState.value.apiKey.isBlank()) {
            Timber.d("SettingsViewModel: API key test skipped, no key configured")
            _uiState.update {
                it.copy(
                    apiKeyTestResult = AnkiDroidTestResult(
                        ok = false,
                        message = context.getString(R.string.error_no_api_key_configured),
                    ),
                )
            }
            return
        }
        _uiState.update { it.copy(isTestingApiKey = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { cloudOcr.testKey() }
            Timber.i("SettingsViewModel: API key test ok=%b", result.isSuccess)
            _uiState.update {
                it.copy(
                    isTestingApiKey = false,
                    apiKeyTestResult = AnkiDroidTestResult(
                        ok = result.isSuccess,
                        message = result.fold(
                            onSuccess = { context.getString(R.string.msg_api_key_valid) },
                            onFailure = { e ->
                                context.getString(
                                    R.string.error_test_failed,
                                    e.message ?: context.getString(R.string.error_unknown),
                                )
                            },
                        ),
                    ),
                )
            }
        }
    }

    fun openDeckPicker() {
        Timber.d("SettingsViewModel: opening deck picker")
        _uiState.update { it.copy(showDeckPicker = true, isLoadingDeckNames = true) }
        viewModelScope.launch {
            val names = withContext(Dispatchers.IO) { ankiSender.getDeckNames() }
            Timber.d("SettingsViewModel: loaded %d deck name(s)", names.size)
            _uiState.update { it.copy(deckNames = names, isLoadingDeckNames = false) }
        }
    }

    fun dismissDeckPicker() {
        _uiState.update { it.copy(showDeckPicker = false) }
    }

    fun openNoteTypePicker() {
        Timber.d("SettingsViewModel: opening note type picker")
        _uiState.update { it.copy(showNoteTypePicker = true, isLoadingNoteTypeNames = true) }
        viewModelScope.launch {
            val names = withContext(Dispatchers.IO) { ankiSender.getNoteTypeNames() }
            Timber.d("SettingsViewModel: loaded %d note type name(s)", names.size)
            _uiState.update { it.copy(noteTypeNames = names, isLoadingNoteTypeNames = false) }
        }
    }

    fun dismissNoteTypePicker() {
        _uiState.update { it.copy(showNoteTypePicker = false) }
    }
}
