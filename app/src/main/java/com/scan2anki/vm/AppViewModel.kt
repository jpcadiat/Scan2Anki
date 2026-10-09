package com.scan2anki.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scan2anki.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class AppUiState(
    val sessionId: Long? = null,
    val restartSessionId: Long? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repo: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var started = false

    fun start() {
        if (started) {
            Timber.v("AppViewModel.start: already bootstrapped, ignoring")
            return
        }
        started = true
        Timber.i("AppViewModel.start: clearing old sessions and creating a fresh one")
        viewModelScope.launch {
            repo.clearAllSessions()
            val id = repo.createSession()
            Timber.i("AppViewModel.start: bootstrap session id=%d", id)
            _uiState.update { it.copy(sessionId = id) }
        }
    }

    fun startNewSession() {
        Timber.i("AppViewModel.startNewSession requested")
        viewModelScope.launch {
            val id = repo.createSession()
            Timber.i("AppViewModel.startNewSession created id=%d", id)
            _uiState.update { it.copy(restartSessionId = id) }
        }
    }

    fun consumeRestart() {
        Timber.v("AppViewModel consuming restartSessionId=%d", _uiState.value.restartSessionId)
        _uiState.update { it.copy(restartSessionId = null) }
    }
}
