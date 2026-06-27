package com.qingyi.hear.core.lyrics

import com.qingyi.hear.domain.MusicSource

/**
 * 单行歌词。
 *
 * @param timeMs 该行开始时间（毫秒），纯文本（无时间轴）行记为 0
 * @param text   歌词文本
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/**
 * 一首歌的完整歌词集合。
 *
 * @param lines  按时间升序排列的歌词行
 * @param source 歌词来源平台
 */
data class Lyrics(
    val lines: List<LyricLine>,
    val source: MusicSource,
)
