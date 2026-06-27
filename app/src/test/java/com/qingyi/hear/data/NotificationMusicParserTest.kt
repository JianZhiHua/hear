package com.qingyi.hear.data

import com.qingyi.hear.domain.MusicSource
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationMusicParserTest {

    @Test
    fun parsesTitleArtistAndAlbumFromNotificationFields() {
        val music = NotificationMusicParser.parse(
            packageName = "com.tencent.qqmusic",
            appName = "QQ音乐",
            title = "Song A",
            text = "Artist A",
            subText = "Album A",
            bigText = null,
            postTime = 123L,
        )

        assertEquals("Song A", music?.title)
        assertEquals("Artist A", music?.artist)
        assertEquals("Album A", music?.album)
        assertEquals(MusicSource.QQ_MUSIC, music?.source)
        assertEquals(123L, music?.timestamp)
    }
}
