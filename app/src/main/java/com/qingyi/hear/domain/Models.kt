package com.qingyi.hear.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class Track(
    val source: String,
    val id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val durationMs: Long? = null,
    val coverUrl: String? = null,
    val resolverId: String? = null,
    val raw: JsonObject = buildJsonObject {},
) {
    val displayArtist: String
        get() = artists.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "未知歌手"

    val displayTitle: String
        get() = "$title - $displayArtist"
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val kind: String,
    val tracks: List<Track> = emptyList(),
    val trackCount: Int? = null,
    val description: String? = null,
    val coverUrl: String? = null,
)

@Serializable
data class StreamUrl(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val expiresAtEpochSeconds: Long? = null,
)

@Serializable
data class Lyrics(
    val text: String,
    val translatedText: String? = null,
)

enum class PlayMode {
    Order,
    Single,
    Shuffle,
}

@Serializable
data class LyricLine(
    val timeMs: Long? = null,
    val text: String,
)

@Serializable
enum class LyricTextAlign {
    Start,
    Center,
    End,
}

@Serializable
enum class LyricColor {
    Primary,
    Light,
    Warm,
    Green,
}

@Serializable
enum class LyricBackgroundStyle {
    Plain,
    Surface,
    Dark,
}

@Serializable
data class LyricSettings(
    val fontSizeSp: Float = 18f,
    val color: LyricColor = LyricColor.Primary,
    val alignment: LyricTextAlign = LyricTextAlign.Center,
    val lineSpacing: Float = 1.35f,
    val backgroundStyle: LyricBackgroundStyle = LyricBackgroundStyle.Surface,
)

enum class AudioQuality(
    val netEaseLevel: String,
    val netEaseEncodeType: String,
) {
    Standard("standard", "mp3"),
    ExHigh("exhigh", "mp3"),
    Lossless("lossless", "flac");

    val displayName: String
        get() = when (this) {
            Standard -> "标准"
            ExHigh -> "高品"
            Lossless -> "无损"
        }

    /** 降级到更低音质，Standard 无法再降 */
    fun fallback(): AudioQuality? = when (this) {
        Lossless -> ExHigh
        ExHigh -> Standard
        Standard -> null
    }
}
