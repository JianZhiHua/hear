package com.qingyi.hear.core.lyrics

import com.qingyi.hear.domain.MusicSource

/**
 * 歌词仓库接口。
 *
 * 根据 [songId] 与 [source] 获取歌词。严格遵守：
 * ❌ 不使用登录 cookie
 * ❌ 不抓包私有接口
 * ❌ 不解密 DRM 内容
 */
interface LyricsRepository {

    /**
     * @param songId 在来源平台中的歌曲标识
     * @param source 来源平台
     * @return 解析后的歌词；无可用歌词时返回 null
     */
    suspend fun getLyrics(songId: String, source: MusicSource): Lyrics?
}
