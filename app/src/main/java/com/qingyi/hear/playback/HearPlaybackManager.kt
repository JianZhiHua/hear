package com.qingyi.hear.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.qingyi.hear.domain.AudioQuality
import com.qingyi.hear.domain.PlayMode
import com.qingyi.hear.domain.Track
import com.qingyi.hear.domain.removeQueueItem
import com.qingyi.hear.domain.trackQueueKey
import com.qingyi.hear.providers.MusicProvider
import com.qingyi.hear.providers.ProviderError
import com.qingyi.hear.storage.PlaybackQueueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class HearPlaybackManager(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val queueStore: PlaybackQueueStore,
    client: OkHttpClient,
    private val providerBySource: Map<String, MusicProvider>,
) {
    private var queueTouched = false
    private var lastPersistedIndex = -2
    private val appContext = context.applicationContext
    private val _queueState = MutableStateFlow(PlaybackQueueState())

    private val controller = HearPlaybackController(appContext, client) { track ->
        val provider = providerBySource[track.source]
            ?: throw ProviderError("未知音乐平台：${track.source}")
        withContext(Dispatchers.IO) {
            provider.resolveStream(track, AudioQuality.ExHigh)
        }
    }

    val player = controller.player
    val state: StateFlow<PlaybackState> = controller.state
    val queueState: StateFlow<PlaybackQueueState> = _queueState.asStateFlow()

    init {
        appScope.launch {
            val snapshot = queueStore.loadSnapshot()
            if (!queueTouched) {
                _queueState.value = PlaybackQueueState(
                    queue = snapshot.queue,
                    currentIndex = snapshot.currentIndex,
                    playMode = snapshot.playMode,
                    volume = snapshot.volume,
                )
                lastPersistedIndex = snapshot.currentIndex
                controller.restoreQueue(snapshot.queue, snapshot.currentIndex, snapshot.playMode, snapshot.volume)
            }
        }
        appScope.launch {
            controller.state.collectLatest { playbackState ->
                val current = _queueState.value
                val newIndex = playbackState.currentIndex
                if (newIndex != current.currentIndex && (newIndex in current.queue.indices || newIndex == -1)) {
                    _queueState.value = current.copy(currentIndex = newIndex)
                    persistQueueIndexIfNeeded(newIndex)
                }
            }
        }
    }

    fun play(track: Track) {
        appScope.launch {
            val current = _queueState.value
            val existingIndex = current.queue.indexOfFirst { trackQueueKey(it) == trackQueueKey(track) }
            val queue = if (existingIndex >= 0) current.queue else current.queue + track
            val targetIndex = if (existingIndex >= 0) existingIndex else queue.lastIndex
            playQueueInternal(queue, targetIndex)
        }
    }

    fun playQueueItem(index: Int) {
        appScope.launch {
            val current = _queueState.value
            if (index in current.queue.indices) {
                playQueueInternal(current.queue, index)
            }
        }
    }

    fun toggle() {
        if (!state.value.isPlaying && _queueState.value.queue.isNotEmpty()) {
            startPlaybackService()
        }
        controller.toggle()
    }

    fun previous() {
        if (_queueState.value.queue.isEmpty()) return
        startPlaybackService()
        controller.previous()
    }

    fun next() {
        if (_queueState.value.queue.isEmpty()) return
        startPlaybackService()
        controller.next()
    }

    fun seekTo(positionMs: Long) {
        controller.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        controller.setVolume(safeVolume)
        _queueState.value = _queueState.value.copy(volume = safeVolume)
        appScope.launch {
            queueStore.saveVolume(safeVolume)
        }
    }

    fun setPlayMode(playMode: PlayMode) {
        controller.setPlayMode(playMode)
        _queueState.value = _queueState.value.copy(playMode = playMode)
        appScope.launch {
            queueStore.savePlayMode(playMode)
        }
    }

    fun removeQueueItem(index: Int) {
        appScope.launch {
            val current = _queueState.value
            val result = removeQueueItem(current.queue, current.currentIndex, index)
            queueTouched = true
            _queueState.value = current.copy(queue = result.queue, currentIndex = result.currentIndex)
            queueStore.saveQueueState(result.queue, result.currentIndex)
            lastPersistedIndex = result.currentIndex

            if (result.queue.isEmpty()) {
                controller.stop(clearMediaItems = true)
                stopPlaybackService()
            } else {
                controller.replaceQueue(
                    tracks = result.queue,
                    currentIndex = result.currentIndex,
                    playMode = current.playMode,
                    startPositionMs = if (result.removedCurrent) 0L else state.value.positionMs,
                    playWhenReady = state.value.isPlaying,
                )
            }
        }
    }

    fun clearQueue() {
        appScope.launch {
            queueTouched = true
            _queueState.value = _queueState.value.copy(queue = emptyList(), currentIndex = -1)
            queueStore.saveQueueState(emptyList(), -1)
            lastPersistedIndex = -1
            controller.stop(clearMediaItems = true)
            stopPlaybackService()
        }
    }

    fun stop() {
        controller.stop(clearMediaItems = false)
        stopPlaybackService()
    }

    fun refreshProgress() {
        controller.refreshProgress()
    }

    fun release() {
        controller.release()
    }

    private suspend fun playQueueInternal(queue: List<Track>, index: Int) {
        if (index !in queue.indices) return
        queueTouched = true
        val playMode = _queueState.value.playMode
        _queueState.value = _queueState.value.copy(queue = queue, currentIndex = index)
        queueStore.saveQueueState(queue, index)
        lastPersistedIndex = index
        controller.playQueue(queue, index, playMode)
        startPlaybackService()
    }

    private suspend fun persistQueueIndexIfNeeded(index: Int) {
        if (index == lastPersistedIndex) return
        val queue = _queueState.value.queue
        if (index in queue.indices || index == -1) {
            queueStore.saveQueueState(queue, index)
            lastPersistedIndex = index
        }
    }

    private fun startPlaybackService() {
        val intent = Intent(appContext, HearPlaybackService::class.java)
            .setAction(HearPlaybackService.ACTION_START)
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopPlaybackService() {
        appContext.stopService(Intent(appContext, HearPlaybackService::class.java))
    }
}

data class PlaybackQueueState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val playMode: PlayMode = PlayMode.Order,
    val volume: Float = 1f,
)
