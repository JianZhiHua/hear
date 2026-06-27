package com.qingyi.hear.core.lyrics

/**
 * 歌词解析器。
 *
 * 支持：
 * - LRC 格式（`[mm:ss.xx] 文本`，同一行可含多个时间标签）
 * - web API 返回的 JSON 歌词结构（网易云 / QQ 最终均产出 LRC 文本，由本类统一解析）
 * - 时间轴解析 `[mm:ss.xx]`
 * - `[offset:xxx]` 全局偏移修正
 */
object LyricsParser {

    /** 匹配 `[00:12.345]` / `[1:02.3]` / `[00:12:345]` 等时间标签。 */
    private val TIME_TAG = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** 元数据标签，如 `[ti:...]`、`[ar:...]`、`[offset:...]`，不作为歌词行。 */
    private val META_TAG = Regex("""^\[([a-zA-Z]+):\s*(.*)]""")

    /** 解析 LRC 文本为按时间升序排列的歌词行列表。 */
    fun parseLrc(lrc: String): List<LyricLine> {
        if (lrc.isBlank()) return emptyList()

        var globalOffset = 0L
        val lines = mutableListOf<LyricLine>()

        for (raw in lrc.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            // 元数据：提取 offset，其余跳过
            val meta = META_TAG.find(line)
            if (meta != null) {
                val key = meta.groupValues[1]
                val value = meta.groupValues[2].trim()
                if (key.equals("offset", ignoreCase = true)) {
                    value.toLongOrNull()?.let { globalOffset = it }
                }
                continue
            }

            val text = TIME_TAG.replace(line, "").trim()
            val tags = TIME_TAG.findAll(line).toList()
            if (tags.isEmpty()) {
                // 纯文本行：仅在确实有内容时保留，时间记为 0（不参与提前高亮）
                if (text.isNotEmpty()) lines += LyricLine(0L, text)
                continue
            }

            for (tag in tags) {
                val min = tag.groupValues[1].toLong()
                val sec = tag.groupValues[2].toLong()
                val ms = tag.groupValues[3].let { frac ->
                    if (frac.isBlank()) 0L else frac.padEnd(3, '0').take(3).toLong()
                }
                val timeMs = min * 60_000L + sec * 1000L + ms + globalOffset
                lines += LyricLine(timeMs, text)
            }
        }

        return lines.sortedBy { it.timeMs }
    }
}
