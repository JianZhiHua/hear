package com.qingyi.hear.domain

private val timeTagRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val metadataTagRegex = Regex("""\[\w+:.*?]""")

/**
 * 解析 LRC 歌词文本
 *
 * 优化点：
 * 1. 支持多种换行符格式（\r\n, \r, \n）
 * 2. 自动过滤无效时间标签（超出合理范围）
 * 3. 处理重复时间标签（取最后一个）
 * 4. 支持元数据标签过滤（[ti:], [ar:] 等）
 * 5. 增强异常处理，防止格式错误导致崩溃
 */
fun parseLyrics(text: String): List<LyricLine> {
    if (text.isBlank()) return emptyList()

    val rawLines = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val timedLines = mutableListOf<LyricLine>()
    val timeTagSet = mutableSetOf<Long>() // 用于去重

    for (line in rawLines) {
        // 跳过元数据标签
        if (metadataTagRegex.matches(line)) continue

        val matches = timeTagRegex.findAll(line).toList()
        if (matches.isEmpty()) continue

        val content = timeTagRegex.replace(line, "").trim()
        if (content.isBlank()) continue

        for (match in matches) {
            val timeMs = match.toTimeMs() ?: continue

            // 验证时间范围（0ms ~ 10小时）
            if (timeMs < 0 || timeMs > 36_000_000L) continue

            // 去重：同一时间戳只保留最后一个
            if (timeTagSet.contains(timeMs)) {
                timedLines.removeAll { it.timeMs == timeMs }
            }
            timeTagSet.add(timeMs)

            timedLines.add(LyricLine(timeMs = timeMs, text = content))
        }
    }

    if (timedLines.isNotEmpty()) {
        return timedLines.sortedBy { it.timeMs ?: Long.MAX_VALUE }
    }

    // 如果没有时间标签，返回纯文本行
    return rawLines
        .filter { !metadataTagRegex.matches(it) }
        .map { LyricLine(text = it) }
}

/**
 * 查找当前活跃的歌词行索引
 *
 * 优化点：
 * 1. 使用二分查找提高性能（O(log n) vs O(n)）
 * 2. 处理边界情况（空列表、无时间标签）
 */
fun activeLyricIndex(lines: List<LyricLine>, positionMs: Long): Int? {
    if (lines.isEmpty()) return null

    // 快速检查：如果第一个时间标签还没到，返回 null
    val firstTimedIndex = lines.indexOfFirst { it.timeMs != null }
    if (firstTimedIndex < 0) return null
    if (positionMs < (lines[firstTimedIndex].timeMs ?: return null)) return null

    // 二分查找最后一个时间 <= positionMs 的行
    var low = firstTimedIndex
    var high = lines.lastIndex
    var result: Int? = null

    while (low <= high) {
        val mid = (low + high) / 2
        val midTime = lines[mid].timeMs

        if (midTime == null) {
            // 跳过无时间标签的行
            high = mid - 1
            continue
        }

        if (midTime <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return result
}

/**
 * 解析时间标签为毫秒数
 *
 * @return 毫秒数，解析失败返回 null
 */
private fun MatchResult.toTimeMs(): Long? {
    return try {
        val minutes = groupValues[1].toLongOrNull() ?: return null
        val seconds = groupValues[2].toLongOrNull() ?: return null

        if (minutes < 0 || minutes > 59) return null
        if (seconds < 0 || seconds > 59) return null

        val fractionStr = groupValues.getOrNull(3).orEmpty()
        val fraction = when {
            fractionStr.isEmpty() -> 0L
            fractionStr.length == 1 -> fractionStr.toLongOrNull()?.let { it * 100 } ?: 0L
            fractionStr.length == 2 -> fractionStr.toLongOrNull()?.let { it * 10 } ?: 0L
            else -> fractionStr.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
        }

        minutes * 60_000L + seconds * 1000L + fraction
    } catch (e: Exception) {
        null
    }
}
