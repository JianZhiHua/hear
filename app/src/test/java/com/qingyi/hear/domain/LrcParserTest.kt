package com.qingyi.hear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesTimestampedLyrics() {
        val lines = parseLyrics(
            """
            [00:01.50]第一句
            [01:02.003]第二句
            """.trimIndent(),
        )

        assertEquals(listOf(1_500L, 62_003L), lines.map { it.timeMs })
        assertEquals(listOf("第一句", "第二句"), lines.map { it.text })
    }

    @Test
    fun expandsMultipleTimeTagsOnOneLine() {
        val lines = parseLyrics("[00:10.00][00:20.00]副歌")

        assertEquals(listOf(10_000L, 20_000L), lines.map { it.timeMs })
        assertEquals(listOf("副歌", "副歌"), lines.map { it.text })
    }

    @Test
    fun keepsPlainTextWhenThereAreNoTimestamps() {
        val lines = parseLyrics(
            """
            纯文本第一行
            纯文本第二行
            """.trimIndent(),
        )

        assertEquals(listOf(null, null), lines.map { it.timeMs })
        assertEquals(listOf("纯文本第一行", "纯文本第二行"), lines.map { it.text })
    }

    @Test
    fun matchesActiveTimedLineByPosition() {
        val lines = parseLyrics(
            """
            [00:01.00]第一句
            [00:05.00]第二句
            [00:09.00]第三句
            """.trimIndent(),
        )

        assertNull(activeLyricIndex(lines, 999L))
        assertEquals(0, activeLyricIndex(lines, 1_000L))
        assertEquals(1, activeLyricIndex(lines, 8_999L))
        assertEquals(2, activeLyricIndex(lines, 12_000L))
    }
}
