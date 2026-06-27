package com.qingyi.hear.core.floating_lyrics

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 悬浮歌词启动器：统一封装权限检测与服务的启动 / 停止 / 切换。
 *
 * 调用方（如 UI）只需通过本对象操作，无需感知 Service 细节。
 */
object FloatingLyricsStarter {

    /** 是否已获得悬浮窗权限。 */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** 悬浮歌词服务是否正在运行。 */
    fun isRunning(): Boolean = FloatingLyricsService.isRunning

    /** 启动悬浮歌词服务。 */
    fun start(context: Context) {
        val intent = Intent(context, FloatingLyricsService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    /** 停止悬浮歌词服务。 */
    fun stop(context: Context) {
        val intent = Intent(context, FloatingLyricsService::class.java).apply {
            action = FloatingLyricsService.ACTION_STOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    /** 切换运行状态：未运行则启动，运行中则停止。 */
    fun toggle(context: Context) {
        if (isRunning()) stop(context) else start(context)
    }
}
