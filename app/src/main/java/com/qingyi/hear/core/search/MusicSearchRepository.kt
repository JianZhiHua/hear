package com.qingyi.hear.core.search

/**
 * 统一音乐搜索仓库接口。
 *
 * 实现需聚合多个来源（网易云 / QQ音乐），返回去重后的统一结果列表。
 * 严格遵守：不使用 cookie、不使用登录态、仅访问可匿名访问的公开接口。
 */
interface MusicSearchRepository {

    /**
     * 按关键词搜索歌曲。
     *
     * @param keyword 搜索关键词（歌曲名 / 歌手）
     * @return 合并并去重后的统一搜索结果列表
     */
    suspend fun search(keyword: String): List<MusicSearchResult>
}
