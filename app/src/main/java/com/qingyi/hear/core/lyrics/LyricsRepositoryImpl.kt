package com.qingyi.hear.core.lyrics

import com.qingyi.hear.domain.MusicSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 歌词仓库实现：按来源分发到对应的抓取器。
 *
 * 任一来源异常都被吞掉并返回 null，避免影响播放体验。
 */
class LyricsRepositoryImpl(
    private val neteaseFetcher: NeteaseLyricsFetcher,
    private val qqFetcher: QQLyricsFetcher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LyricsRepository {

    override suspend fun getLyrics(songId: String, source: MusicSource): Lyrics? =
        withContext(ioDispatcher) {
            runCatching {
                when (source) {
                    MusicSource.NETEASE_CLOUD -> neteaseFetcher.fetch(songId)
                    MusicSource.QQ_MUSIC -> qqFetcher.fetch(songId)
                    else -> null
                }
            }.getOrNull()
        }
}
