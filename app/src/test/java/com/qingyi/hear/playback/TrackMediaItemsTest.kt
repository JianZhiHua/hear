package com.qingyi.hear.playback

import com.qingyi.hear.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackMediaItemsTest {
    @Test
    fun trackMediaDescriptorCarriesStableIdAndMetadata() {
        val track = Track(
            source = "qq",
            id = "123",
            title = "一首歌",
            artists = listOf("歌手"),
            album = "专辑",
            durationMs = 180_000L,
            coverUrl = "https://img.test/cover.jpg",
            resolverId = "song:::media",
        )

        val descriptor = track.toMediaDescriptor()

        assertEquals("qq:123:song:::media", descriptor.mediaId)
        assertEquals("一首歌", descriptor.title)
        assertEquals("歌手", descriptor.artist)
        assertEquals("专辑", descriptor.album)
        assertEquals(180_000L, descriptor.durationMs)
        assertEquals("https://img.test/cover.jpg", descriptor.artworkUriString)
    }

    @Test
    fun streamUriRoundTripsMediaId() {
        val track = Track(source = "netease", id = "456", title = "Song", resolverId = "456")
        val descriptor = track.toMediaDescriptor()

        assertEquals(descriptor.mediaId, mediaIdFromStreamUriString(descriptor.uriString))
    }
}
