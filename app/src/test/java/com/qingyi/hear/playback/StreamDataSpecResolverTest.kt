package com.qingyi.hear.playback

import com.qingyi.hear.domain.StreamUrl
import com.qingyi.hear.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamDataSpecResolverTest {
    @Test
    fun resolveTrackStreamRequestAddsProviderUrlAndHeaders() {
        val track = Track(source = "qq", id = "1", title = "Track", resolverId = "mid:::media")
        val descriptor = track.toMediaDescriptor()

        val resolved = resolveTrackStreamRequest(
            mediaId = descriptor.mediaId,
            baseHeaders = mapOf("Range" to "bytes=0-"),
            findTrack = { mediaId -> if (mediaId == descriptor.mediaId) track else null },
            resolveStream = {
                StreamUrl(
                    url = "http://cdn.test/song.mp3",
                    headers = mapOf("Referer" to "https://y.qq.com/", "Cookie" to "uin=o1"),
                )
            },
        )

        assertEquals("http://cdn.test/song.mp3", resolved.url)
        assertEquals("bytes=0-", resolved.headers["Range"])
        assertEquals("https://y.qq.com/", resolved.headers["Referer"])
        assertEquals("uin=o1", resolved.headers["Cookie"])
        assertEquals(descriptor.mediaId, resolved.key)
    }
}
