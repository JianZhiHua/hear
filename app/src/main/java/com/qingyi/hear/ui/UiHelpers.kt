package com.qingyi.hear.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.qingyi.hear.domain.LyricBackgroundStyle
import com.qingyi.hear.domain.LyricColor
import com.qingyi.hear.domain.LyricTextAlign
import com.qingyi.hear.domain.PlayMode
import com.qingyi.hear.domain.Track
import com.qingyi.hear.storage.LibraryStore
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle

@Composable
internal fun freshBackground(): Color = MaterialTheme.colorScheme.background

@Composable
internal fun freshSurface(): Color = MaterialTheme.colorScheme.surface

@Composable
internal fun freshSurfaceSoft(): Color = MaterialTheme.colorScheme.surfaceVariant

@Composable
internal fun freshMint(): Color =
    if (isSystemInDarkTheme()) Color(0xFF8BD9B3) else Color(0xFF62B58D)

@Composable
internal fun freshLeaf(): Color = MaterialTheme.colorScheme.primary

@Composable
internal fun platformColor(source: String?): Color =
    when (source) {
        "qq" -> Color(0xFF1FA463)
        "netease" -> Color(0xFFD6453D)
        LibraryStore.LOCAL_KIND -> freshMint()
        else -> MaterialTheme.colorScheme.primary
    }

@Composable
internal fun lyricBackgroundColor(style: LyricBackgroundStyle): Color =
    when (style) {
        LyricBackgroundStyle.Plain -> MaterialTheme.colorScheme.surface
        LyricBackgroundStyle.Surface -> MaterialTheme.colorScheme.surfaceVariant
        LyricBackgroundStyle.Dark -> Color(0xFF111318)
    }

@Composable
internal fun lyricNormalColor(color: LyricColor): Color =
    when (color) {
        LyricColor.Primary -> MaterialTheme.colorScheme.onSurfaceVariant
        LyricColor.Light -> MaterialTheme.colorScheme.onSurface
        LyricColor.Warm -> Color(0xFF9B5D00)
        LyricColor.Green -> Color(0xFF1B7F47)
    }

@Composable
internal fun lyricActiveColor(color: LyricColor): Color =
    when (color) {
        LyricColor.Primary -> MaterialTheme.colorScheme.primary
        LyricColor.Light -> MaterialTheme.colorScheme.onSurface
        LyricColor.Warm -> Color(0xFFE07A00)
        LyricColor.Green -> Color(0xFF00A05A)
    }

internal fun trackSubtitle(track: Track): String =
    buildList {
        add(track.displayArtist)
        track.album?.takeIf(String::isNotBlank)?.let(::add)
        formatDuration(track.durationMs)?.let(::add)
        add(platformName(track.source))
    }.joinToString(" · ")

internal fun effectiveDuration(state: HearUiState): Long =
    state.durationMs.takeIf { it > 0L } ?: state.currentTrack?.durationMs ?: 0L

internal fun LyricTextAlign.toTextAlign(): androidx.compose.ui.text.style.TextAlign =
    when (this) {
        LyricTextAlign.Start -> androidx.compose.ui.text.style.TextAlign.Start
        LyricTextAlign.Center -> androidx.compose.ui.text.style.TextAlign.Center
        LyricTextAlign.End -> androidx.compose.ui.text.style.TextAlign.End
    }

internal fun LyricColor.label(): String =
    when (this) {
        LyricColor.Primary -> "主题"
        LyricColor.Light -> "纯白"
        LyricColor.Warm -> "柔和"
        LyricColor.Green -> "霓虹"
    }

internal fun LyricTextAlign.label(): String =
    when (this) {
        LyricTextAlign.Start -> "左对齐"
        LyricTextAlign.Center -> "居中"
        LyricTextAlign.End -> "右对齐"
    }

internal fun LyricBackgroundStyle.label(): String =
    when (this) {
        LyricBackgroundStyle.Plain -> "纯净白"
        LyricBackgroundStyle.Surface -> "柔和"
        LyricBackgroundStyle.Dark -> "深邃黑"
    }

internal fun PlayMode.label(): String =
    when (this) {
        PlayMode.Order -> "顺序播放"
        PlayMode.Single -> "单曲循环"
        PlayMode.Shuffle -> "随机播放"
    }

internal fun PlayMode.next(): PlayMode =
    when (this) {
        PlayMode.Order -> PlayMode.Single
        PlayMode.Single -> PlayMode.Shuffle
        PlayMode.Shuffle -> PlayMode.Order
    }

internal fun PlayMode.icon(): androidx.compose.ui.graphics.vector.ImageVector =
    when (this) {
        PlayMode.Order -> QueueMusic
        PlayMode.Single -> RepeatOne
        PlayMode.Shuffle -> Shuffle
    }

internal fun platformName(source: String): String =
    when (source) {
        "qq" -> "QQ 音乐"
        "netease" -> "网易云音乐"
        LibraryStore.LOCAL_KIND -> "本地歌单"
        else -> source
    }

internal fun platformShortName(source: String): String =
    when (source) {
        "qq" -> "QQ"
        "netease" -> "网易云"
        LibraryStore.LOCAL_KIND -> "本地"
        else -> source.uppercase().take(2)
    }

internal fun formatDuration(durationMs: Long?): String? {
    val totalSeconds = durationMs?.div(1000) ?: return null
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

internal fun formatClock(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
