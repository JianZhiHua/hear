package com.qingyi.hear.domain

import kotlin.random.Random

fun nextQueueIndex(
    queueSize: Int,
    currentIndex: Int,
    playMode: PlayMode,
    randomIndex: (Int) -> Int = { Random.nextInt(it) },
): Int? {
    if (queueSize <= 0) return null
    val safeCurrent = currentIndex.takeIf { it in 0 until queueSize } ?: -1
    return when (playMode) {
        PlayMode.Order -> if (safeCurrent < 0) 0 else (safeCurrent + 1) % queueSize
        PlayMode.Single -> if (safeCurrent < 0) 0 else safeCurrent
        PlayMode.Shuffle -> shuffledNextIndex(queueSize, safeCurrent, randomIndex)
    }
}

fun previousQueueIndex(queueSize: Int, currentIndex: Int): Int? {
    if (queueSize <= 0) return null
    val safeCurrent = currentIndex.takeIf { it in 0 until queueSize } ?: 0
    return (safeCurrent - 1 + queueSize) % queueSize
}

fun removeQueueItem(
    queue: List<Track>,
    currentIndex: Int,
    removeIndex: Int,
): QueueRemovalResult {
    if (removeIndex !in queue.indices) {
        return QueueRemovalResult(queue, currentIndex.takeIf { it in queue.indices } ?: -1, removedCurrent = false)
    }

    val newQueue = queue.filterIndexed { index, _ -> index != removeIndex }
    if (newQueue.isEmpty()) {
        return QueueRemovalResult(newQueue, -1, removedCurrent = removeIndex == currentIndex)
    }

    val newIndex = when {
        currentIndex !in queue.indices -> -1
        removeIndex == currentIndex -> currentIndex.coerceAtMost(newQueue.lastIndex)
        removeIndex < currentIndex -> currentIndex - 1
        else -> currentIndex.coerceAtMost(newQueue.lastIndex)
    }
    return QueueRemovalResult(newQueue, newIndex, removedCurrent = removeIndex == currentIndex)
}

fun trackQueueKey(track: Track): String =
    listOf(track.source, track.id.ifBlank { track.resolverId.orEmpty() })
        .joinToString(":")

data class QueueRemovalResult(
    val queue: List<Track>,
    val currentIndex: Int,
    val removedCurrent: Boolean,
)

private fun shuffledNextIndex(
    queueSize: Int,
    currentIndex: Int,
    randomIndex: (Int) -> Int,
): Int {
    if (queueSize == 1 || currentIndex !in 0 until queueSize) return 0
    val candidate = randomIndex(queueSize - 1).coerceIn(0, queueSize - 2)
    return if (candidate >= currentIndex) candidate + 1 else candidate
}
