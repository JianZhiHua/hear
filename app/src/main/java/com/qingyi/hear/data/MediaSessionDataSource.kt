package com.qingyi.hear.data

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import com.qingyi.hear.domain.MusicInfo
import com.qingyi.hear.domain.MusicSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通过系统 MediaSessionManager 读取所有活跃的媒体会话
 */
class MediaSessionDataSource(private val context: Context) {

    companion object {
        private const val TAG = "MediaSessionDS"
    }

    private val _activeMusic = MutableStateFlow<MusicInfo?>(null)
    val activeMusic: StateFlow<MusicInfo?> = _activeMusic.asStateFlow()

    private val listenerComponent = ComponentName(context, HearNotificationListener::class.java)
    private var mediaSessionManager: MediaSessionManager? = null
    private var registered = false

    private val callback = object : MediaSessionManager.OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
            Log.d(TAG, "Active sessions changed: ${controllers?.size ?: 0}")
            updateFromControllers(controllers)
        }
    }

    /**
     * 检查是否有通知监听权限
     */
    fun isEnabled(): Boolean {
        val sm = getManager() ?: return false
        return try {
            sm.getActiveSessions(listenerComponent)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * 注册 session 变化监听
     */
    fun start() {
        if (registered) return
        val sm = getManager() ?: return
        try {
            Log.i(TAG, "Registering active session listener for $listenerComponent")
            sm.addOnActiveSessionsChangedListener(callback, listenerComponent)
            registered = true
            // 初始扫描
            updateFromControllers(sm.getActiveSessions(listenerComponent))
            Log.i(TAG, "Started listening for active sessions")
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification listener permission: ${e.message}")
        }
    }

    /**
     * 取消监听
     */
    fun stop() {
        if (!registered) return
        val sm = getManager() ?: return
        try {
            sm.removeOnActiveSessionsChangedListener(callback)
            registered = false
        } catch (_: Exception) {}
    }

    /**
     * 手动刷新
     */
    fun isListening(): Boolean = registered

    fun refresh() {
        val sm = getManager() ?: return
        try {
            updateFromControllers(sm.getActiveSessions(listenerComponent))
        } catch (_: Exception) {}
    }

    private fun getManager(): MediaSessionManager? {
        if (mediaSessionManager == null) {
            mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        }
        return mediaSessionManager
    }

    private fun updateFromControllers(controllers: MutableList<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            Log.d(TAG, "No active media sessions")
            _activeMusic.value = null
            return
        }

        // 优先找正在播放的 session
        val playing = controllers.firstOrNull { ctrl ->
            ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()

        if (playing == null) {
            _activeMusic.value = null
            return
        }

        val metadata = playing.getMetadata()
        val state = playing.playbackState
        val pkg = playing.packageName.orEmpty()
        val source = MusicSource.fromPackage(pkg)

        // 跳过自己
        if (pkg == context.packageName) {
            Log.d(TAG, "Skip own session: $pkg")
            return
        }

        val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.description?.title?.toString()
            ?: return
        val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.description?.subtitle?.toString()
            ?: ""
        val album = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM)
        val duration = metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        // 尝试获取应用名
        val appName = try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) { pkg }

        val musicInfo = MusicInfo(
            title = title,
            artist = artist,
            album = album,
            appPackage = pkg,
            appName = appName,
            isPlaying = isPlaying,
            position = position,
            duration = duration,
            source = source,
        )

        _activeMusic.value = musicInfo
        Log.d(TAG, "Active music: $title by $artist ($appName, playing=$isPlaying)")
    }
}
