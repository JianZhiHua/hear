package com.qingyi.hear.data

import android.os.SystemClock
import com.qingyi.hear.domain.MusicInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicAggregationEngine(
    private val mediaSessionSource: MediaSessionDataSource,
    private val notificationSource: NotificationMusicDataSource,
    private val historyStore: HistoryStore,
    private val scope: CoroutineScope,
) {
    private companion object {
        private const val PLAYBACK_TICK_MS = 100L
    }

    private val _currentMusic = MutableStateFlow<MusicInfo?>(null)
    val currentMusic: StateFlow<MusicInfo?> = _currentMusic.asStateFlow()

    val history: Flow<List<HistoryEntry>> = historyStore.history

    private var lastSavedHash: String? = null
    private var started = false
    @Volatile private var playbackSnapshot: MusicInfo? = null
    @Volatile private var playbackAnchorPosition: Long = 0L
    @Volatile private var playbackAnchorRealtime: Long = 0L

    fun start() {
        if (started) return
        started = true
        mediaSessionSource.start()
        scope.launch {
            combine(
                mediaSessionSource.activeMusic,
                notificationSource.activeMusic,
            ) { media, notification ->
                media ?: notification
            }.collectLatest { music ->
                playbackSnapshot = music
                _currentMusic.value = music
                if (music != null && music.isPlaying) {
                    playbackAnchorPosition = music.position
                    playbackAnchorRealtime = SystemClock.elapsedRealtime()
                    saveToHistory(music)
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(PLAYBACK_TICK_MS)
                val snapshot = playbackSnapshot ?: continue
                if (!snapshot.isPlaying) continue

                val progressed = snapshot.copy(
                    position = playbackAnchorPosition + (SystemClock.elapsedRealtime() - playbackAnchorRealtime),
                )
                if (progressed.position != _currentMusic.value?.position) {
                    _currentMusic.value = progressed
                }
            }
        }
    }

    fun stop() {
        mediaSessionSource.stop()
    }

    fun refresh() {
        mediaSessionSource.refresh()
    }

    private suspend fun saveToHistory(music: MusicInfo) {
        val hash = music.hash
        if (hash == lastSavedHash) return
        lastSavedHash = hash
        historyStore.addEntry(music)
    }
}
