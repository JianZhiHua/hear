@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.qingyi.hear.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.qingyi.hear.domain.PlayMode
import com.qingyi.hear.domain.StreamUrl
import com.qingyi.hear.domain.Track
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import javax.net.ssl.SSLException

class HearPlaybackController(
    context: Context,
    callFactory: Call.Factory,
    private val streamResolver: suspend (Track) -> StreamUrl,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlaybackState())
    private var queue: List<Track> = emptyList()
    private var tracksByMediaId: Map<String, Track> = emptyMap()

    private val dataSourceFactory = ResolvingDataSource.Factory(
        OkHttpDataSource.Factory(callFactory),
        ResolvingDataSource.Resolver { dataSpec ->
            resolveTrackDataSpec(
                dataSpec = dataSpec,
                findTrack = { mediaId -> tracksByMediaId[mediaId] },
                resolveStream = { track ->
                    runBlocking(Dispatchers.IO) { streamResolver(track) }
                },
            )
        },
    )

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
        .also { exoPlayer ->
            exoPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            exoPlayer.addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateState(isPlaying = isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateState(isBuffering = playbackState == Player.STATE_BUFFERING)
                    }

                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                        updateState(
                            currentTrack = mediaItem?.mediaId?.let { tracksByMediaId[it] },
                            currentIndex = safeCurrentIndex(),
                            positionMs = safePosition(),
                            durationMs = safeDuration(),
                            errorMessage = null,
                        )
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) {
                        updateState(
                            currentTrack = currentTrack(),
                            currentIndex = safeCurrentIndex(),
                            positionMs = safePosition(),
                            durationMs = safeDuration(),
                        )
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        updateState()
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        updateState()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        updateState(
                            isBuffering = false,
                            errorMessage = describePlaybackError(error),
                        )
                    }
                },
            )
        }

    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun restoreQueue(tracks: List<Track>, currentIndex: Int, playMode: PlayMode, volume: Float) {
        queue = tracks
        tracksByMediaId = tracks.associateBy(::trackMediaId)
        if (tracks.isEmpty()) {
            player.clearMediaItems()
            setVolume(volume)
            _state.value = PlaybackState(volume = player.volume)
            return
        }
        player.setMediaItems(tracks.map { it.toMediaItem() }, currentIndex.takeIf { it in tracks.indices } ?: 0, 0L)
        setPlayMode(playMode)
        setVolume(volume)
        updateState(
            currentTrack = tracks.getOrNull(currentIndex),
            currentIndex = currentIndex.takeIf { it in tracks.indices } ?: -1,
            positionMs = 0L,
            durationMs = tracks.getOrNull(currentIndex)?.durationMs ?: 0L,
            errorMessage = null,
        )
    }

    fun playQueue(tracks: List<Track>, currentIndex: Int, playMode: PlayMode, startPositionMs: Long = 0L) {
        if (currentIndex !in tracks.indices) return
        queue = tracks
        tracksByMediaId = tracks.associateBy(::trackMediaId)
        player.setMediaItems(tracks.map { it.toMediaItem() }, currentIndex, startPositionMs.coerceAtLeast(0L))
        setPlayMode(playMode)
        updateState(
            currentTrack = tracks[currentIndex],
            currentIndex = currentIndex,
            positionMs = startPositionMs.coerceAtLeast(0L),
            durationMs = tracks[currentIndex].durationMs ?: 0L,
            errorMessage = null,
        )
        player.prepare()
        player.play()
    }

    fun replaceQueue(
        tracks: List<Track>,
        currentIndex: Int,
        playMode: PlayMode,
        startPositionMs: Long = safePosition(),
        playWhenReady: Boolean = player.playWhenReady,
    ) {
        queue = tracks
        tracksByMediaId = tracks.associateBy(::trackMediaId)
        if (tracks.isEmpty()) {
            player.stop()
            player.clearMediaItems()
            _state.value = PlaybackState(volume = player.volume)
            return
        }
        val safeIndex = currentIndex.takeIf { it in tracks.indices } ?: 0
        player.setMediaItems(tracks.map { it.toMediaItem() }, safeIndex, startPositionMs.coerceAtLeast(0L))
        setPlayMode(playMode)
        updateState(
            currentTrack = tracks[safeIndex],
            currentIndex = safeIndex,
            positionMs = startPositionMs.coerceAtLeast(0L),
            durationMs = tracks[safeIndex].durationMs ?: 0L,
            errorMessage = null,
        )
        if (playWhenReady) {
            player.prepare()
            player.play()
        } else {
            player.playWhenReady = false
        }
    }

    fun appendQueueItem(
        track: Track,
        tracks: List<Track>,
        currentIndex: Int,
        playMode: PlayMode,
    ) {
        val oldMediaItemCount = player.mediaItemCount
        queue = tracks
        tracksByMediaId = tracks.associateBy(::trackMediaId)
        setPlayMode(playMode)

        if (oldMediaItemCount == tracks.lastIndex && oldMediaItemCount > 0) {
            player.addMediaItem(track.toMediaItem())
            updateState(
                currentTrack = currentTrack(),
                currentIndex = safeCurrentIndex(),
                errorMessage = null,
            )
            return
        }

        val safeIndex = currentIndex.takeIf { it in tracks.indices } ?: 0
        val startPositionMs = safePosition()
        val playWhenReady = player.playWhenReady
        player.setMediaItems(tracks.map { it.toMediaItem() }, safeIndex, startPositionMs.coerceAtLeast(0L))
        updateState(
            currentTrack = tracks[safeIndex],
            currentIndex = safeIndex,
            positionMs = startPositionMs.coerceAtLeast(0L),
            durationMs = tracks[safeIndex].durationMs ?: 0L,
            errorMessage = null,
        )
        if (playWhenReady) {
            player.prepare()
            player.play()
        } else {
            player.playWhenReady = false
        }
    }

    fun toggle() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                player.prepare()
            }
            player.play()
        }
    }

    fun resume() {
        player.play()
    }

    fun previous() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
        }
    }

    fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        refreshProgress()
    }

    fun setVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        player.volume = safeVolume
        updateState(volume = safeVolume)
    }

    fun setPlayMode(playMode: PlayMode) {
        player.repeatMode = when (playMode) {
            PlayMode.Single -> Player.REPEAT_MODE_ONE
            PlayMode.Order,
            PlayMode.Shuffle,
            -> Player.REPEAT_MODE_ALL
        }
        player.shuffleModeEnabled = playMode == PlayMode.Shuffle
        updateState()
    }

    fun stop(clearMediaItems: Boolean = false) {
        player.stop()
        if (clearMediaItems) {
            queue = emptyList()
            tracksByMediaId = emptyMap()
            player.clearMediaItems()
            _state.value = PlaybackState(volume = player.volume)
        } else {
            updateState(isPlaying = false, isBuffering = false)
        }
    }

    fun refreshProgress() {
        updateState(
            currentTrack = currentTrack(),
            currentIndex = safeCurrentIndex(),
            positionMs = safePosition(),
            durationMs = safeDuration(),
        )
    }

    fun release() {
        player.release()
    }

    private fun currentTrack(): Track? = player.currentMediaItem?.mediaId?.let { tracksByMediaId[it] }

    private fun updateState(
        currentTrack: Track? = _state.value.currentTrack,
        currentIndex: Int = _state.value.currentIndex,
        isPlaying: Boolean = player.isPlaying,
        isBuffering: Boolean = _state.value.isBuffering,
        positionMs: Long = safePosition(),
        durationMs: Long = safeDuration(),
        volume: Float = player.volume,
        errorMessage: String? = _state.value.errorMessage,
    ) {
        _state.value = _state.value.copy(
            currentTrack = currentTrack,
            currentIndex = currentIndex,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            positionMs = positionMs,
            durationMs = durationMs,
            volume = volume,
            errorMessage = errorMessage,
        )
    }

    private fun safeCurrentIndex(): Int =
        player.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1

    private fun safePosition(): Long = player.currentPosition.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L

    private fun safeDuration(): Long {
        val duration = player.duration
        return duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: currentTrack()?.durationMs
            ?: _state.value.currentTrack?.durationMs
            ?: 0L
    }

    private fun describePlaybackError(error: PlaybackException): String {
        val causes = generateSequence(error as Throwable?) { it.cause }.toList()
        causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.let { httpError ->
            val message = httpError.responseMessage?.takeIf { it.isNotBlank() }
            return listOfNotNull("HTTP ${httpError.responseCode}", message).joinToString("：")
        }
        causes.firstOrNull { it.message?.contains("Cleartext", ignoreCase = true) == true }?.let {
            return "音频直链使用 HTTP，系统已阻止明文播放请求"
        }
        causes.filterIsInstance<UnknownHostException>().firstOrNull()?.let {
            return "无法连接到音频服务器，请检查网络或 DNS"
        }
        causes.filterIsInstance<SocketTimeoutException>().firstOrNull()?.let {
            return "连接音频服务器超时"
        }
        causes.filterIsInstance<SSLException>().firstOrNull()?.let {
            return "音频服务器 HTTPS 证书或握手失败"
        }
        causes.filterIsInstance<IOException>().firstOrNull()?.message?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        val detail = causes.asReversed().firstNotNullOfOrNull { it.message?.takeIf(String::isNotBlank) }
        return detail ?: error.errorCodeName
    }
}

data class PlaybackState(
    val currentTrack: Track? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val errorMessage: String? = null,
)
