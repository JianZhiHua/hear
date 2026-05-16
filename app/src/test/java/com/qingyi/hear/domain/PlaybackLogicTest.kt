package com.qingyi.hear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackLogicTest {
    @Test
    fun orderModeMovesToNextAndWraps() {
        assertEquals(1, nextQueueIndex(queueSize = 3, currentIndex = 0, playMode = PlayMode.Order))
        assertEquals(0, nextQueueIndex(queueSize = 3, currentIndex = 2, playMode = PlayMode.Order))
        assertEquals(0, nextQueueIndex(queueSize = 3, currentIndex = -1, playMode = PlayMode.Order))
    }

    @Test
    fun singleModeKeepsCurrentTrack() {
        assertEquals(2, nextQueueIndex(queueSize = 4, currentIndex = 2, playMode = PlayMode.Single))
        assertEquals(0, nextQueueIndex(queueSize = 4, currentIndex = -1, playMode = PlayMode.Single))
    }

    @Test
    fun shuffleModeDoesNotRepeatCurrentWhenPossible() {
        val next = nextQueueIndex(
            queueSize = 5,
            currentIndex = 2,
            playMode = PlayMode.Shuffle,
            randomIndex = { 2 },
        )

        assertEquals(3, next)
        assertFalse(next == 2)
    }

    @Test
    fun removeQueueItemFixesCurrentIndex() {
        val queue = listOf(track("1"), track("2"), track("3"), track("4"))

        val beforeCurrent = removeQueueItem(queue, currentIndex = 2, removeIndex = 0)
        assertEquals(listOf("2", "3", "4"), beforeCurrent.queue.map { it.id })
        assertEquals(1, beforeCurrent.currentIndex)
        assertFalse(beforeCurrent.removedCurrent)

        val current = removeQueueItem(queue, currentIndex = 2, removeIndex = 2)
        assertEquals(listOf("1", "2", "4"), current.queue.map { it.id })
        assertEquals(2, current.currentIndex)
        assertEquals(true, current.removedCurrent)
    }

    private fun track(id: String): Track =
        Track(source = "qq", id = id, title = "Track $id")
}
