package com.qingyi.hear.core.floating_lyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.ViewGroup
import androidx.core.app.NotificationCompat
import com.qingyi.hear.HearApplication
import com.qingyi.hear.R
import com.qingyi.hear.core.lyrics.LyricsRepository
import com.qingyi.hear.core.lyrics.LyricsSyncEngine
import com.qingyi.hear.core.search.MusicSearchRepository
import com.qingyi.hear.core.search.MusicSearchResult
import com.qingyi.hear.domain.MusicInfo
import com.qingyi.hear.domain.MusicSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FloatingLyricsService : Service() {

    companion object {
        private const val TAG = "FloatingLyrics"
        const val ACTION_STOP = "com.qingyi.hear.action.STOP_FLOATING_LYRICS"
        private const val CHANNEL_ID = "hear_floating_lyrics"
        private const val NOTIFICATION_ID = 0xF1

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var controller: WindowManagerController
    private var floatingView: FloatingLyricsView? = null

    private lateinit var searchRepo: MusicSearchRepository
    private lateinit var lyricsRepo: LyricsRepository
    private lateinit var syncEngine: LyricsSyncEngine
    private lateinit var currentMusic: kotlinx.coroutines.flow.StateFlow<MusicInfo?>

    @Volatile private var currentPosition = 0L
    @Volatile private var currentSongKey: String? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val container = (application as HearApplication).container
        searchRepo = container.musicSearchRepository
        lyricsRepo = container.lyricsRepository
        syncEngine = container.lyricsSyncEngine
        currentMusic = container.aggregationEngine.currentMusic

        startForegroundCompat()
        setupFloatingView()
        observeLyrics()
        observePlayback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        scope.cancel()
        floatingView?.let { controller.removeView(it) }
        floatingView = null
        syncEngine.clearLyrics()
    }

    private fun setupFloatingView() {
        controller = WindowManagerController(this)
        if (!controller.canDrawOverlays()) {
            Log.w(TAG, "No SYSTEM_ALERT_WINDOW permission, stopping.")
            stopSelf()
            return
        }
        val view = FloatingLyricsView(this) { event ->
            when (event) {
                FloatingLyricsView.Event.Tap -> floatingView?.toggleCollapsed()
                FloatingLyricsView.Event.LongPress -> stopSelf()
                is FloatingLyricsView.Event.Drag -> controller.moveBy(event.dx, event.dy)
            }
        }
        floatingView = view
        controller.addView(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun observeLyrics() {
        scope.launch {
            syncEngine.currentLyric.collectLatest { line ->
                refreshFloatingView(line?.text)
            }
        }
    }

    private fun observePlayback() {
        scope.launch {
            currentMusic.collectLatest { music ->
                if (music == null) {
                    currentPosition = 0L
                    currentSongKey = null
                    syncEngine.clearLyrics()
                    floatingView?.update(null, null)
                    return@collectLatest
                }

                currentPosition = music.position
                syncEngine.updatePosition(music.position)
                resolveLyricsFor(music)
                refreshFloatingView()
            }
        }
    }

    private fun refreshFloatingView(currentText: String? = null) {
        val view = floatingView ?: return
        val current = currentText ?: syncEngine.getCurrentLyric(currentPosition)?.text
        val next = syncEngine.getNextLyric(currentPosition)
        view.update(current, next?.text)
    }

    private fun resolveLyricsFor(music: MusicInfo) {
        val key = music.title + "|" + music.artist
        if (key == currentSongKey) return
        currentSongKey = key
        syncEngine.clearLyrics()
        scope.launch(Dispatchers.IO) {
            val results = runCatching { searchRepo.search(music.title) }.getOrDefault(emptyList())
            val match = pickMatch(results, music) ?: return@launch
            val lyrics = lyricsRepo.getLyrics(match.songId, match.source) ?: return@launch
            syncEngine.setLyrics(lyrics.lines)
        }
    }

    private fun pickMatch(
        results: List<MusicSearchResult>,
        music: MusicInfo,
    ): MusicSearchResult? {
        if (results.isEmpty()) return null
        val knownSource = music.source == MusicSource.NETEASE_CLOUD || music.source == MusicSource.QQ_MUSIC
        val byArtist = results.firstOrNull { r ->
            (!knownSource || r.source == music.source) &&
                music.artist.isNotBlank() &&
                r.artist.contains(music.artist, ignoreCase = true)
        }
        return byArtist ?: results.firstOrNull { !knownSource || it.source == music.source } ?: results.first()
    }

    private fun startForegroundCompat() {
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                description = "保持桌面悬浮歌词运行"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, FloatingLyricsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("桌面悬浮歌词运行中")
            .setSmallIcon(R.drawable.ic_hear)
            .setOngoing(true)
            .addAction(0, "关闭", stopIntent)
            .build()
    }
}
