package com.qingyi.hear.core.lyrics

/**
 * 歌词抓取器：根据 [songId] 请求歌词数据并解析为 [Lyrics]。
 *
 * 各来源分别实现，均不使用 cookie / 登录态。
 */
fun interface LyricsFetcher {

    /** @return 解析后的歌词；无可用歌词时返回 null */
    suspend fun fetch(songId: String): Lyrics?
}
