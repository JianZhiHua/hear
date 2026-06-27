package com.qingyi.hear.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsParserTest {

    @Test
    fun parsesTimestampedLyrics() {
        val lines = LyricsParser.parseLrc(
            """
            [00:01.50]First line
            [01:02.003]Second line
            """.trimIndent(),
        )

        assertEquals(listOf(1_500L, 62_003L), lines.map { it.timeMs })
        assertEquals(listOf("First line", "Second line"), lines.map { it.text })
    }

    @Test
    fun expandsMultipleTimeTagsOnOneLine() {
        val lines = LyricsParser.parseLrc("[00:10.00][00:20.00]Chorus")

        assertEquals(listOf(10_000L, 20_000L), lines.map { it.timeMs })
        assertEquals(listOf("Chorus", "Chorus"), lines.map { it.text })
    }

    @Test
    fun appliesGlobalOffset() {
        val lines = LyricsParser.parseLrc(
            """
            [offset:250]
            [00:01.00]Delayed
            """.trimIndent(),
        )

        assertEquals(listOf(1_250L), lines.map { it.timeMs })
        assertEquals(listOf("Delayed"), lines.map { it.text })
    }

    @Test
    fun keepsPlainTextWhenThereAreNoTimestamps() {
        val lines = LyricsParser.parseLrc(
            """
            Plain first line
            Plain second line
            """.trimIndent(),
        )

        assertEquals(listOf(0L, 0L), lines.map { it.timeMs })
        assertEquals(listOf("Plain first line", "Plain second line"), lines.map { it.text })
    }
}
