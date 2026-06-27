package com.qingyi.hear.core.search

import com.qingyi.hear.domain.MusicSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicSearchRepositoryImplTest {

    @Test
    fun mergesAndDeduplicatesAnonymousSources() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = MusicSearchRepositoryImpl(
            netease = FakeSearchDataSource(
                listOf(
                    result("Song A", "Artist", MusicSource.NETEASE_CLOUD, "netease-1"),
                    result("Song B", "Artist B", MusicSource.NETEASE_CLOUD, "netease-2"),
                ),
            ),
            qq = FakeSearchDataSource(
                listOf(
                    result(" song a ", "artist", MusicSource.QQ_MUSIC, "qq-1"),
                    result("Song C", "Artist C", MusicSource.QQ_MUSIC, "qq-2"),
                ),
            ),
            ioDispatcher = dispatcher,
        )

        val results = repository.search("song")

        assertEquals(listOf("netease-1", "netease-2", "qq-2"), results.map { it.songId })
    }

    @Test
    fun sourceFailureDoesNotHideOtherResults() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = MusicSearchRepositoryImpl(
            netease = FailingSearchDataSource,
            qq = FakeSearchDataSource(listOf(result("Song", "Artist", MusicSource.QQ_MUSIC, "qq-1"))),
            ioDispatcher = dispatcher,
        )

        val results = repository.search("song")

        assertEquals(listOf("qq-1"), results.map { it.songId })
    }

    private fun result(
        title: String,
        artist: String,
        source: MusicSource,
        songId: String,
    ) = MusicSearchResult(
        title = title,
        artist = artist,
        source = source,
        songId = songId,
    )
}

private class FakeSearchDataSource(
    private val results: List<MusicSearchResult>,
) : MusicSearchDataSource {
    override suspend fun search(keyword: String, limit: Int): List<MusicSearchResult> =
        results.take(limit)
}

private data object FailingSearchDataSource : MusicSearchDataSource {
    override suspend fun search(keyword: String, limit: Int): List<MusicSearchResult> =
        error("source unavailable")
}
