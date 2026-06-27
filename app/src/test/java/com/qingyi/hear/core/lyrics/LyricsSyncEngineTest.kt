package com.qingyi.hear.core.lyrics

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsSyncEngineTest {

    @Test
    fun matchesCurrentAndNextLyricByPosition() {
        val engine = LyricsSyncEngine()
        engine.setLyrics(
            listOf(
                LyricLine(1_000L, "First"),
                LyricLine(5_000L, "Second"),
                LyricLine(9_000L, "Third"),
            ),
        )

        assertNull(engine.getCurrentLyric(999L))
        assertEquals(LyricLine(1_000L, "First"), engine.getCurrentLyric(1_000L))
        assertEquals(LyricLine(5_000L, "Second"), engine.getCurrentLyric(8_999L))
        assertEquals(LyricLine(9_000L, "Third"), engine.getCurrentLyric(12_000L))
        assertEquals(LyricLine(9_000L, "Third"), engine.getNextLyric(5_000L))
    }

    @Test
    fun appliesManualOffset() {
        val engine = LyricsSyncEngine()
        engine.setLyrics(listOf(LyricLine(1_000L, "First")))
        engine.setOffset(500L)

        assertNull(engine.getCurrentLyric(1_499L))
        assertEquals(LyricLine(1_000L, "First"), engine.getCurrentLyric(1_500L))
    }

    @Test
    fun currentLyricFlowRecomputesWhenLyricsAreSetAfterPosition() = runTest {
        val engine = LyricsSyncEngine()
        val values = mutableListOf<LyricLine?>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.currentLyric.take(2).toList(values)
        }

        engine.updatePosition(5_000L)
        engine.setLyrics(listOf(LyricLine(1_000L, "First")))

        assertEquals(listOf(null, LyricLine(1_000L, "First")), values)
        job.cancel()
    }
}
