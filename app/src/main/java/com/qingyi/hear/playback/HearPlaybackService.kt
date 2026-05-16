@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.qingyi.hear.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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

    override fun onCreate() {
        super.onCreate()
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

    companion object {
        const val ACTION_START = "com.qingyi.hear.playback.START"
        private const val CHANNEL_ID = "hear_playback"
        private const val NOTIFICATION_ID = 1001
    }
}
