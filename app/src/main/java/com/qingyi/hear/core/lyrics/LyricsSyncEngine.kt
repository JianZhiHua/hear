package com.qingyi.hear.core.lyrics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 歌词同步引擎。
 *
 * 职责：
 * - 维护当前歌词列表与播放进度；
 * - 根据 [positionMs] 匹配当前应高亮的歌词行；
 * - 以 [Flow] 形式输出当前歌词行（仅在变化时发射）；
 * - 支持手动偏移修正 [offsetMs] 与下一句预加载。
 *
 * 数据来源：由外部（如 [com.qingyi.hear.core.floating_lyrics.FloatingLyricsService]）
 * 调用 [setLyrics] / [updatePosition] 驱动。
 */
class LyricsSyncEngine {

    /** 升序排列的歌词行。 */
    @Volatile
    private var lines: List<LyricLine> = emptyList()

    /** 手动偏移（毫秒），正值延后高亮，负值提前高亮。 */
    @Volatile
    var offsetMs: Long = 0L
        private set

    private val _state = MutableStateFlow(SyncState(positionMs = 0L, revision = 0L))

    /** 当前歌词行的实时流，仅在实际变化时发射。 */
    val currentLyric: Flow<LyricLine?> = _state
        .map { getCurrentLyric(it.positionMs) }
        .distinctUntilChanged()

    /** 设置歌词列表（会自动按时间排序）。 */
    fun setLyrics(lyrics: List<LyricLine>) {
        lines = lyrics.sortedBy { it.timeMs }
        invalidate()
    }

    /** 清空歌词。 */
    fun clearLyrics() {
        lines = emptyList()
        invalidate()
    }

    /** 设置手动偏移。 */
    fun setOffset(ms: Long) {
        offsetMs = ms
        invalidate()
    }

    /** 更新播放进度。 */
    fun updatePosition(positionMs: Long) {
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    /**
     * 根据播放进度匹配当前歌词行。
     *
     * 取时间 <= 调整后进度的最后一行；进度在第一句之前时返回 null。
     */
    fun getCurrentLyric(positionMs: Long): LyricLine? {
        val adjusted = positionMs - offsetMs
        if (adjusted < 0) return null
        val list = lines
        if (list.isEmpty()) return null
        var result: LyricLine? = null
        for (line in list) {
            if (line.timeMs <= adjusted) {
                result = line
            } else {
                break
            }
        }
        return result
    }

    /**
     * 下一句歌词（用于预加载 / 副歌词展示）。
     * 取时间 > 调整后进度的第一行。
     */
    fun getNextLyric(positionMs: Long): LyricLine? {
        val adjusted = positionMs - offsetMs
        return lines.firstOrNull { it.timeMs > adjusted }
    }

    private fun invalidate() {
        val state = _state.value
        _state.value = state.copy(revision = state.revision + 1)
    }

    private data class SyncState(
        val positionMs: Long,
        val revision: Long,
    )
}
