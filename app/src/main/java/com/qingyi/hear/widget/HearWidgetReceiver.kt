package com.qingyi.hear.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.qingyi.hear.MainActivity
import com.qingyi.hear.R

/**
 * 桌面小组件：显示当前播放曲目，支持 上一首/播放暂停/下一首 控制。
 *
 * 点击按钮 → 启动 MainActivity 并附带 action → MainActivity 在 onCreate/onNewIntent 中
 * 解析 action 并调用 ViewModel 对应方法。
 */
class HearWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.qingyi.hear.widget.TOGGLE"
        const val ACTION_PREVIOUS = "com.qingyi.hear.widget.PREVIOUS"
        const val ACTION_NEXT = "com.qingyi.hear.widget.NEXT"
        const val ACTION_UPDATE = "com.qingyi.hear.widget.UPDATE"

        /** 由 PlaybackManager 在曲目/播放状态变化时调用 */
        fun notifyUpdate(context: Context) {
            val intent = Intent(context, HearWidgetReceiver::class.java).apply {
                action = ACTION_UPDATE
            }
            context.sendBroadcast(intent)
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_hear)

            // 从 SharedPreferences 读取最近播放状态
            val prefs = context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
            val title = prefs.getString("title", "听见") ?: "听见"
            val artist = prefs.getString("artist", "未在播放") ?: "未在播放"
            val isPlaying = prefs.getBoolean("isPlaying", false)

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(
                R.id.widget_toggle,
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
            )

            // 点击整个 widget 打开 app
            val launchIntent = Intent(context, MainActivity::class.java)
            val launchPi = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_title, launchPi)
            views.setOnClickPendingIntent(R.id.widget_artist, launchPi)

            // 控制按钮
            views.setOnClickPendingIntent(R.id.widget_prev, buttonPendingIntent(context, ACTION_PREVIOUS, 1))
            views.setOnClickPendingIntent(R.id.widget_toggle, buttonPendingIntent(context, ACTION_TOGGLE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, buttonPendingIntent(context, ACTION_NEXT, 3))

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun buttonPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, HearWidgetReceiver::class.java),
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }
}
