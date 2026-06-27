package com.qingyi.hear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MusicInfoTest {

    @Test
    fun hashIgnoresArtistNoiseForSameTrack() {
        val base = MusicInfo(
            title = "Song A",
            artist = "Artist A",
            album = "Album A",
            appPackage = "com.tencent.qqmusic",
            appName = "QQ Music",
            isPlaying = true,
            duration = 180_000L,
            source = MusicSource.QQ_MUSIC,
        )
        val noisy = base.copy(artist = "Artist A / 蓝牙歌词")

        assertEquals(base.hash, noisy.hash)
    }

    @Test
    fun hashSeparatesDifferentSongs() {
        val first = MusicInfo(
            title = "Song A",
            artist = "Artist A",
            album = "Album A",
            appPackage = "com.tencent.qqmusic",
            appName = "QQ Music",
            isPlaying = true,
            duration = 180_000L,
            source = MusicSource.QQ_MUSIC,
        )
        val second = first.copy(title = "Song B")

        assertNotEquals(first.hash, second.hash)
    }
}
