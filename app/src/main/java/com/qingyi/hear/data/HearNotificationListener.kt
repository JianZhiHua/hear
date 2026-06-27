package com.qingyi.hear.data

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.qingyi.hear.HearApplication

/**
 * 通知监听服务（辅助数据源）
 *
 * 作为 MediaSession 的补充，监听音乐 APP 的通知。
 * 目前主要用于确保系统授予通知监听权限，
 * MediaSessionDataSource 通过 MediaSessionManager 读取 session。
 */
class HearNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "HearNotifListener"
        private val MUSIC_PACKAGES = setOf(
            "com.netease.cloudmusic",
            "com.tencent.qqmusic",
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg in MUSIC_PACKAGES) {
            Log.d(TAG, "Music notification from $pkg")
            (applicationContext as? HearApplication)
                ?.container
                ?.notificationMusicSource
                ?.onNotificationPosted(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg in MUSIC_PACKAGES) {
            (applicationContext as? HearApplication)
                ?.container
                ?.notificationMusicSource
                ?.onNotificationRemoved(sbn)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        (applicationContext as? HearApplication)
            ?.container
            ?.notificationMusicSource
            ?.syncActiveNotifications(activeNotifications)
    }
}
