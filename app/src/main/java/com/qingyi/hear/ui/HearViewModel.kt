package com.qingyi.hear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingyi.hear.HearApplication
import com.qingyi.hear.data.HistoryEntry
import com.qingyi.hear.domain.MusicInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HearViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as HearApplication).container
    private val engine = container.aggregationEngine

    private val _state = MutableStateFlow(MusicUiState())
    val state: StateFlow<MusicUiState> = _state.asStateFlow()

    init {
        engine.start()
        checkNotificationListener()
        viewModelScope.launch {
            engine.currentMusic.collectLatest { music ->
                _state.value = _state.value.copy(currentMusic = music)
            }
        }
        viewModelScope.launch {
            engine.history.collectLatest { history ->
                _state.value = _state.value.copy(history = history)
            }
        }
    }

    fun refresh() {
        checkNotificationListener()
        engine.refresh()
    }

    fun checkNotificationListener() {
        val enabled = container.mediaSessionSource.isEnabled()
        _state.value = _state.value.copy(isNotificationListenerEnabled = enabled)
        if (enabled && !container.mediaSessionSource.isListening()) {
            container.mediaSessionSource.start()
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
    }
}

data class MusicUiState(
    val currentMusic: MusicInfo? = null,
    val history: List<HistoryEntry> = emptyList(),
    val isNotificationListenerEnabled: Boolean = false,
)
