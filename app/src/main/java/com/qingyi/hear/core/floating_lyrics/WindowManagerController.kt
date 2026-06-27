package com.qingyi.hear.core.floating_lyrics

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 悬浮窗控制器：封装 [WindowManager] 的 addView / updateView / removeView，
 * 并负责悬浮窗位置（拖动）的维护。
 *
 * 使用 [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]，需 SYSTEM_ALERT_WINDOW 权限。
 */
class WindowManagerController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var attachedView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    /** 是否已获得悬浮窗权限。 */
    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    /** 当前是否已添加悬浮视图。 */
    fun isAttached(): Boolean = attachedView != null

    /** 添加悬浮视图。 */
    fun addView(view: View, width: Int, height: Int) {
        if (attachedView != null) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val flags = (
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        val params = WindowManager.LayoutParams(width, height, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 240
        }
        windowManager.addView(view, params)
        attachedView = view
        layoutParams = params
    }

    /** 刷新视图布局（尺寸 / 内容变化后调用）。 */
    fun updateView(view: View) {
        layoutParams?.let { windowManager.updateViewLayout(view, it) }
    }

    /** 按增量移动悬浮窗位置。 */
    fun moveBy(dx: Int, dy: Int) {
        val params = layoutParams ?: return
        params.x += dx
        params.y += dy
        attachedView?.let { windowManager.updateViewLayout(it, params) }
    }

    /** 移除悬浮视图。 */
    fun removeView(view: View) {
        runCatching { windowManager.removeView(view) }
        attachedView = null
        layoutParams = null
    }
}
