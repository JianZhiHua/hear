package com.qingyi.hear.domain

/**
 * Source platform.
 */
enum class MusicSource(val displayName: String) {
    NETEASE_CLOUD("网易云音乐"),
    QQ_MUSIC("QQ音乐"),
    LOCAL_PLAYER("本地播放器"),
    UNKNOWN("未知");

    companion object {
        fun fromPackage(pkg: String): MusicSource = when {
            pkg.contains("netease.cloudmusic", ignoreCase = true) -> NETEASE_CLOUD
            pkg.contains("tencent.qqmusic", ignoreCase = true) -> QQ_MUSIC
            else -> UNKNOWN
        }
    }
}

/**
 * Current playing music snapshot.
 */
data class MusicInfo(
    val title: String,
    val artist: String,
    val album: String? = null,
    val appPackage: String,
    val appName: String,
    val isPlaying: Boolean,
    val position: Long = 0L,
    val duration: Long = 0L,
    val source: MusicSource,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val displayArtist: String
        get() = artist.takeIf { it.isNotBlank() } ?: "未知歌手"

    /**
     * Stable track key for history dedupe.
     *
     * Intentionally ignores artist noise because QQ's Bluetooth lyric mode
     * can mutate artist/title text in notifications while the track stays the
     * same.
     */
    val hash: String
        get() = buildString {
            append(source.name)
            append('|')
            append(appPackage)
            append('|')
            append(title.trim().lowercase())
            append('|')
            append(album.orEmpty().trim().lowercase())
            append('|')
            append(duration.takeIf { it > 0L } ?: 0L)
        }.hashCode().toString()
}
