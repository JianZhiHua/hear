package com.qingyi.hear.domain

private val timeTagRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

fun parseLyrics(text: String): List<LyricLine> {
    val rawLines = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val timedLines = rawLines.flatMap { line ->
        val matches = timeTagRegex.findAll(line).toList()
        if (matches.isEmpty()) {
            emptyList()
        } else {
            val content = timeTagRegex.replace(line, "").trim()
            matches.map { match ->
                LyricLine(timeMs = match.toTimeMs(), text = content)
            }
        }
    }

    if (timedLines.isNotEmpty()) {
        return timedLines
            .filter { it.text.isNotBlank() }
            .sortedBy { it.timeMs ?: Long.MAX_VALUE }
    }

    return rawLines.map { LyricLine(text = it) }
}

fun activeLyricIndex(lines: List<LyricLine>, positionMs: Long): Int? {
    var activeIndex: Int? = null
    lines.forEachIndexed { index, line ->
        val timeMs = line.timeMs ?: return@forEachIndexed
        if (timeMs <= positionMs) {
            activeIndex = index
        } else {
            return activeIndex
        }
    }
    return activeIndex
}

private fun MatchResult.toTimeMs(): Long {
    val minutes = groupValues[1].toLongOrNull() ?: 0L
    val seconds = groupValues[2].toLongOrNull() ?: 0L
    val fraction = groupValues.getOrNull(3)
        .orEmpty()
        .padEnd(3, '0')
        .take(3)
        .toLongOrNull()
        ?: 0L
    return minutes * 60_000L + seconds * 1000L + fraction
}
