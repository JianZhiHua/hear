package com.qingyi.hear.data

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import com.qingyi.hear.domain.MusicInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification-driven music snapshot source.
 *
 * This is a fallback data source for devices where MediaSession discovery is
 * unreliable. It accepts music notifications from supported apps and exposes
 * the latest parsed [MusicInfo].
 */
class NotificationMusicDataSource(private val context: Context) {

    companion object {
        private const val TAG = "NotifMusicDS"
        private val MUSIC_PACKAGES = setOf(
            "com.netease.cloudmusic",
            "com.tencent.qqmusic",
        )
    }

    private val _activeMusic = MutableStateFlow<MusicInfo?>(null)
    val activeMusic: StateFlow<MusicInfo?> = _activeMusic.asStateFlow()

    private val activeByPackage = linkedMapOf<String, MusicInfo>()

    fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg !in MUSIC_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val appName = getAppName(pkg)

        val music = NotificationMusicParser.parse(
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            subText = subText,
            bigText = bigText,
            postTime = sbn.postTime,
            isPlaying = true,
        ) ?: return

        activeByPackage[pkg] = music
        _activeMusic.value = music
        Log.d(TAG, "Parsed music notification from $pkg: ${music.title} / ${music.artist}")
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg !in MUSIC_PACKAGES) return

        activeByPackage.remove(pkg)
        _activeMusic.value = activeByPackage.values.lastOrNull()
    }

    fun syncActiveNotifications(notifications: Array<StatusBarNotification>) {
        activeByPackage.clear()
        notifications.forEach { sbn ->
            if (sbn.packageName in MUSIC_PACKAGES) {
                onNotificationPosted(sbn)
            }
        }
        if (activeByPackage.isEmpty()) {
            _activeMusic.value = null
        } else {
            _activeMusic.value = activeByPackage.values.last()
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }
}
