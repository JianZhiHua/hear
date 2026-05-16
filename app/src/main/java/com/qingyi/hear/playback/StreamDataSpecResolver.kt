@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.qingyi.hear.playback

import androidx.media3.datasource.DataSpec
import com.qingyi.hear.domain.StreamUrl
import com.qingyi.hear.domain.Track
import java.io.IOException

fun resolveTrackDataSpec(
    dataSpec: DataSpec,
    findTrack: (String) -> Track?,
    resolveStream: (Track) -> StreamUrl,
): DataSpec {
    val mediaId = mediaIdFromStreamUri(dataSpec.uri) ?: return dataSpec
    val resolved = resolveTrackStreamRequest(
        mediaId = mediaId,
        baseHeaders = dataSpec.httpRequestHeaders,
        findTrack = findTrack,
        resolveStream = resolveStream,
    )
    return dataSpec.buildUpon()
        .setUri(resolved.url)
        .setHttpRequestHeaders(resolved.headers)
        .setKey(resolved.key)
        .build()
}

fun resolveTrackStreamRequest(
    mediaId: String,
    baseHeaders: Map<String, String> = emptyMap(),
    findTrack: (String) -> Track?,
    resolveStream: (Track) -> StreamUrl,
): ResolvedTrackStream {
    val track = findTrack(mediaId)
        ?: throw IOException("播放队列中找不到这首歌")
    val stream = resolveStream(track)
    if (stream.url.isBlank()) {
        throw IOException("没有获取到可播放链接")
    }
    return ResolvedTrackStream(
        url = stream.url,
        headers = baseHeaders + stream.headers,
        key = mediaId,
    )
}

data class ResolvedTrackStream(
    val url: String,
    val headers: Map<String, String>,
    val key: String,
)
