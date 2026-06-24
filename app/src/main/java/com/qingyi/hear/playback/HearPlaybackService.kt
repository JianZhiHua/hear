@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.qingyi.hear.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.qingyi.hear.HearApplication
import com.qingyi.hear.MainActivity
import com.qingyi.hear.R

@Suppress("DEPRECATION")
class HearPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hadAudioFocus = false

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ensureNotificationChannel()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_notification_channel)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_hear)
        setMediaNotificationProvider(notificationProvider)
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)

        val session = MediaSession.Builder(this, appContainer.playbackManager.player)
            .setSessionActivity(sessionActivity())
            .setMediaButtonPreferences(mediaButtonPreferences())
            .setCallback(
                object : MediaSession.Callback {
                    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                    override fun onPlayerCommandRequest(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        playerCommand: Int,
                    ): Int {
                        if (playerCommand == Player.COMMAND_STOP) {
                            appContainer.playbackManager.stop()
                        }
                        return SessionResult.RESULT_SUCCESS
                    }
                },
            )
            .build()
        mediaSession = session
        addSession(session)
        triggerNotificationUpdate()

        // 监听播放状态变化，管理 AudioFocus
        session.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    requestAudioFocus()
                } else {
                    // 不立即释放，保留焦点以便快速恢复
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> {
                        releaseAudioFocus()
                    }
                    Player.STATE_ENDED -> {
                        // 播放结束，可以延迟释放焦点
                    }
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            startForegroundForPlayback()
        }
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_START) {
            triggerNotificationUpdate()
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playback alive when the task is swiped away. The notification stop action ends the service.
    }

    override fun onDestroy() {
        releaseAudioFocus()
        mediaSession?.let { session ->
            if (isSessionAdded(session)) {
                removeSession(session)
            }
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private val appContainer
        get() = (application as HearApplication).container

    private fun sessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun mediaButtonPreferences(): List<CommandButton> =
        listOf(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .setDisplayName("上一首")
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_PLAY)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setDisplayName("播放/暂停")
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .setDisplayName("下一首")
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            CommandButton.Builder(CommandButton.ICON_STOP)
                .setPlayerCommand(Player.COMMAND_STOP)
                .setDisplayName("停止")
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun startForegroundForPlayback() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hear)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("正在准备播放")
            .setContentIntent(sessionActivity())
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
    }

    /**
     * 请求音频焦点
     *
     * 优化点：
     * 1. 使用 AudioFocusRequest（API 26+）
     * 2. 处理焦点丢失和恢复
     * 3. 支持延迟聚焦（Duck）
     */
    private fun requestAudioFocus() {
        if (hadAudioFocus) return

        val am = audioManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .build()

            audioFocusRequest = focusRequest
            val result = am.requestAudioFocus(focusRequest)
            hadAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = am.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
            hadAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    /**
     * 释放音频焦点
     */
    private fun releaseAudioFocus() {
        if (!hadAudioFocus) return

        val am = audioManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusListener)
        }

        audioFocusRequest = null
        hadAudioFocus = false
    }

    /**
     * 音频焦点变化监听器
     *
     * 处理逻辑：
     * - AUDIOFOCUS_LOSS: 长期丢失，暂停播放
     * - AUDIOFOCUS_LOSS_TRANSIENT: 短暂丢失，暂停播放
     * - AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: 短暂丢失，降低音量
     * - AUDIOFOCUS_GAIN: 获得焦点，恢复播放/音量
     */
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val player = mediaSession?.player ?: return@OnAudioFocusChangeListener

        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 长期丢失焦点（如电话呼入）
                player.pause()
                hadAudioFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // 短暂丢失焦点（如通知音）
                player.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 短暂丢失，可以降低音量继续播放
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android O+ 系统会自动 Duck
                } else {
                    player.volume = 0.2f
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // 获得焦点
                if (!player.isPlaying && player.playbackState != Player.STATE_ENDED) {
                    player.play()
                }
                player.volume = 1.0f
                hadAudioFocus = true
            }
        }
    }

    companion object {
        const val ACTION_START = "com.qingyi.hear.playback.START"
        private const val CHANNEL_ID = "hear_playback"
        private const val NOTIFICATION_ID = 1001
    }
}
