package com.qingyi.hear.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.qingyi.hear.domain.Track
import com.qingyi.hear.domain.trackQueueKey
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

private const val STREAM_SCHEME = "hear"
private const val STREAM_AUTHORITY = "stream"

fun trackMediaId(track: Track): String =
    listOf(trackQueueKey(track), track.resolverId.orEmpty())
        .joinToString(":")

fun trackStreamUriString(track: Track): String =
    "hear://stream/${urlEncode(trackMediaId(track))}"

fun mediaIdFromStreamUri(uri: Uri): String? =
    uri.takeIf { it.scheme == STREAM_SCHEME && it.authority == STREAM_AUTHORITY }
        ?.lastPathSegment
        ?.takeIf { it.isNotBlank() }

fun mediaIdFromStreamUriString(uriString: String): String? {
    val uri = runCatching { URI(uriString) }.getOrNull() ?: return null
    if (uri.scheme != STREAM_SCHEME || uri.host != STREAM_AUTHORITY) return null
    return uri.rawPath
        ?.trimStart('/')
        ?.takeIf { it.isNotBlank() }
        ?.let(::urlDecode)
}

fun Track.toMediaDescriptor(): TrackMediaDescriptor =
    TrackMediaDescriptor(
        mediaId = trackMediaId(this),
        uriString = trackStreamUriString(this),
        title = title,
        artist = displayArtist,
        album = album,
        durationMs = durationMs,
        artworkUriString = coverUrl,
    )

fun Track.toMediaItem(): MediaItem {
    val descriptor = toMediaDescriptor()
    val metadata = MediaMetadata.Builder()
        .setTitle(descriptor.title)
        .setArtist(descriptor.artist)
        .setAlbumTitle(descriptor.album)
        .setDurationMs(descriptor.durationMs)
        .apply {
            descriptor.artworkUriString?.takeIf { it.isNotBlank() }?.let { setArtworkUri(Uri.parse(it)) }
        }
        .build()
    return MediaItem.Builder()
        .setMediaId(descriptor.mediaId)
        .setUri(descriptor.uriString)
        .setMediaMetadata(metadata)
        .setTag(this)
        .build()
}

data class TrackMediaDescriptor(
    val mediaId: String,
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val artworkUriString: String?,
)

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

private fun urlDecode(value: String): String =
    URLDecoder.decode(value, Charsets.UTF_8.name())
