package com.qingyi.hear.core.search

import com.qingyi.hear.domain.MusicSource

/**
 * 统一的音乐搜索结果数据模型。
 *
 * 仅包含搜索元数据，不包含任何播放链接。
 *
 * @param title    歌曲标题
 * @param artist   歌手（多歌手以 / 分隔）
 * @param album    专辑名，可能为空
 * @param duration 时长（毫秒），可能为空
 * @param source   来源平台
 * @param songId   在来源平台中的唯一歌曲标识（网易云为数字 id，QQ 为 songmid）
 */
data class MusicSearchResult(
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long? = null,
    val source: MusicSource,
    val songId: String,
)
