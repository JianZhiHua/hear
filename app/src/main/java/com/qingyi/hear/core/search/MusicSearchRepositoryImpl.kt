package com.qingyi.hear.core.search

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 聚合搜索仓库实现。
 *
 * 策略：
 * 1. 并发请求网易云 + QQ音乐；
 * 2. 任一来源失败不影响另一来源（容错降级）；
 * 3. 合并结果后按 `title + artist` 去重（忽略大小写 / 空白）；
 * 4. 返回统一列表。
 *
 * 不做任何登录逻辑，不处理播放链接，仅搜索元数据。
 */
class MusicSearchRepositoryImpl(
    private val netease: MusicSearchDataSource,
    private val qq: MusicSearchDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MusicSearchRepository {

    override suspend fun search(keyword: String): List<MusicSearchResult> =
        withContext(ioDispatcher) {
            coroutineScope {
                val neteaseDeferred = async {
                    runCatching { netease.search(keyword) }.getOrDefault(emptyList())
                }
                val qqDeferred = async {
                    runCatching { qq.search(keyword) }.getOrDefault(emptyList())
                }

                val combined = neteaseDeferred.await() + qqDeferred.await()
                combined.deduplicate()
            }
        }

    /** 按 title + artist 去重，保留首次出现的（优先网易云）。 */
    private fun List<MusicSearchResult>.deduplicate(): List<MusicSearchResult> {
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<MusicSearchResult>()
        for (item in this) {
            val key = normalize(item.title) + "|" + normalize(item.artist)
            if (seen.add(key)) {
                result.add(item)
            }
        }
        return result
    }

    private fun normalize(s: String): String =
        s.trim().lowercase().replace(Regex("\\s+"), " ")
}
