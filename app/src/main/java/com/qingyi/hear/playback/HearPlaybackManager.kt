package com.qingyi.hear.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.qingyi.hear.domain.PlayMode
import com.qingyi.hear.domain.Track
import com.qingyi.hear.domain.removeQueueItem
import com.qingyi.hear.domain.trackQueueKey
import com.qingyi.hear.providers.MusicProvider
import com.qingyi.hear.providers.ProviderError
import com.qingyi.hear.storage.PlaybackQueueStore
import com.qingyi.hear.widget.HearWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
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
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var sleepTimerEndAtMs: Long? = null
    private val appContext = context.applicationContext
    private val _queueState = MutableStateFlow(PlaybackQueueState())
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)

    private val controller = HearPlaybackController(appContext, client) { track ->
        val provider = providerBySource[track.source]
            ?: throw ProviderError("未知音乐平台：${track.source}")
        withContext(Dispatchers.IO) {
            provider.resolveStream(track)
        }
    }

    val player = controller.player
    val state: StateFlow<PlaybackState> = controller.state
    val queueState: StateFlow<PlaybackQueueState> = _queueState.asStateFlow()
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

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
                if (playbackState.isPlaying || playbackState.isBuffering) {
                    startProgressTicker()
                } else {
                    stopProgressTicker()
                    controller.refreshProgress()
                }
                val current = _queueState.value
                val newIndex = playbackState.currentIndex
                if (newIndex != current.currentIndex && (newIndex in current.queue.indices || newIndex == -1)) {
                    _queueState.value = current.copy(currentIndex = newIndex)
                    persistQueueIndexIfNeeded(newIndex)
                }
                // 更新桌面小组件状态
                updateWidgetState(playbackState)
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

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        appScope.launch {
            if (tracks.isEmpty()) return@launch
            val targetIndex = startIndex.coerceIn(tracks.indices)
            playQueueInternal(tracks, targetIndex)
        }
    }

    fun addToQueue(track: Track): Boolean {
        val trackKey = trackQueueKey(track)
        if (_queueState.value.queue.any { trackQueueKey(it) == trackKey }) {
            return false
        }
        appScope.launch {
            val current = _queueState.value
            if (current.queue.any { trackQueueKey(it) == trackKey }) {
                return@launch
            }
            val queue = current.queue + track
            val targetIndex = current.currentIndex.takeIf { it in current.queue.indices } ?: 0
            queueTouched = true
            _queueState.value = current.copy(queue = queue, currentIndex = targetIndex)
            queueStore.saveQueueState(queue, targetIndex)
            lastPersistedIndex = targetIndex
            controller.appendQueueItem(
                track = track,
                tracks = queue,
                currentIndex = targetIndex,
                playMode = current.playMode,
            )
        }
        return true
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


    /**
     * 设置定时停止。minutes=0 表示取消。
     */
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndAtMs = null
        _sleepTimerRemainingMs.value = null
        if (minutes <= 0) return
        val durationMs = minutes * 60_000L
        sleepTimerEndAtMs = System.currentTimeMillis() + durationMs
        sleepTimerJob = appScope.launch {
            while (isActive) {
                val remaining = sleepTimerEndAtMs!! - System.currentTimeMillis()
                if (remaining <= 0) {
                    _sleepTimerRemainingMs.value = null
                    controller.toggle() // pause
                    break
                }
                _sleepTimerRemainingMs.value = remaining
                delay(1000L)
            }
        }
    }

    fun cancelSleepTimer() {
        setSleepTimer(0)
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
        stopProgressTicker()
        sleepTimerJob?.cancel()
        controller.release()
    }

    private fun startProgressTicker() {
        if (progressJob?.isActive == true) return
        progressJob = appScope.launch {
            while (isActive) {
                controller.refreshProgress()
                delay(PROGRESS_REFRESH_MS)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
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

    private fun updateWidgetState(playbackState: PlaybackState) {
        val track = playbackState.currentTrack
        appContext.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
            .edit()
            .putString("title", track?.title ?: "听见")
            .putString("artist", track?.displayArtist ?: "未在播放")
            .putBoolean("isPlaying", playbackState.isPlaying)
            .apply()
        HearWidgetReceiver.notifyUpdate(appContext)
    }

    private companion object {
        const val PROGRESS_REFRESH_MS = 500L
    }
}

data class PlaybackQueueState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val playMode: PlayMode = PlayMode.Order,
    val volume: Float = 1f,
)
