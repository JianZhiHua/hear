package com.qingyi.hear.core.floating_lyrics

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * 桌面悬浮歌词视图。
 *
 * UI：
 * - 当前行（较大、高亮）
 * - 下一行（可选、较小、弱化）
 * - 半透明圆角背景（Material 3 风格配色）
 *
 * 交互（由 [callback] 上抛给 Service 处理）：
 * - 单击：暂停 / 显示切换
 * - 长按：关闭悬浮窗
 * - 拖动：改变位置
 *
 * 动态字体大小随屏幕宽度适配。
 */
class FloatingLyricsView(
    context: Context,
    private val callback: Callback,
) : LinearLayout(context) {

    /** 交互回调。 */
    fun interface Callback {
        fun onEvent(event: Event)
    }

    sealed class Event {
        data object Tap : Event()
        data object LongPress : Event()
        data class Drag(val dx: Int, val dy: Int) : Event()
    }

    private val currentLine: TextView
    private val nextLine: TextView

    private var collapsed = false

    // 手势
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            callback.onEvent(Event.Tap)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            callback.onEvent(Event.LongPress)
        }
    })
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var moved = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        val padH = dp(16)
        val padV = dp(10)
        setPadding(padH, padV, padH, padV)
        background = buildBackground()

        currentLine = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
            setLineSpacing(2f, 1f)
        }
        nextLine = TextView(context).apply {
            setTextColor(0xCCFFFFFF.toInt())
            setLineSpacing(2f, 1f)
        }
        applyTextSizes()

        val lpCurrent = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.CENTER }
        val lpNext = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER
            topMargin = dp(4)
        }
        addView(currentLine, lpCurrent)
        addView(nextLine, lpNext)

        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastTouchX).toInt()
                    val dy = (event.rawY - lastTouchY).toInt()
                    if (abs(event.rawX - touchStartX) > TOUCH_SLOP ||
                        abs(event.rawY - touchStartY) > TOUCH_SLOP
                    ) {
                        moved = true
                    }
                    if (moved && (dx != 0 || dy != 0)) {
                        callback.onEvent(Event.Drag(dx, dy))
                    }
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                }
            }
            true
        }
    }

    /** 更新展示的歌词。 */
    fun update(current: String?, next: String?) {
        currentLine.text = current.orEmpty()
        nextLine.text = next.orEmpty()
        val hasContent = !current.isNullOrBlank() || !next.isNullOrBlank()
        visibility = if (hasContent) View.VISIBLE else View.GONE
        currentLine.visibility = if (collapsed || current.isNullOrBlank()) View.GONE else View.VISIBLE
        nextLine.visibility = if (collapsed || next.isNullOrBlank()) View.GONE else View.VISIBLE
        requestLayout()
    }

    /** 切换折叠（隐藏歌词文本，仅保留小圆点占位）。 */
    fun toggleCollapsed() {
        collapsed = !collapsed
        currentLine.visibility = if (collapsed) View.GONE else View.VISIBLE
        nextLine.visibility = if (collapsed || nextLine.text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun buildBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(20).toFloat()
        setColor(0x66000000) // 半透明黑
        setStroke(dp(1), 0x33FFFFFF)
    }

    /** 根据屏幕宽度动态计算字号。 */
    private fun applyTextSizes() {
        val widthDp = resources.configuration.screenWidthDp.coerceAtLeast(320)
        val scale = (widthDp / 360f).coerceIn(1f, 1.8f)
        currentLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * scale)
        nextLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scale)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()

    private companion object {
        const val TOUCH_SLOP = 8f
    }
}
