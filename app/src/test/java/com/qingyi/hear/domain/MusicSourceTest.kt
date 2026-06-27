package com.qingyi.hear.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicSourceTest {

    @Test
    fun mapsKnownMusicPackages() {
        assertEquals(MusicSource.NETEASE_CLOUD, MusicSource.fromPackage("com.netease.cloudmusic"))
        assertEquals(MusicSource.QQ_MUSIC, MusicSource.fromPackage("com.tencent.qqmusic"))
        assertEquals(MusicSource.UNKNOWN, MusicSource.fromPackage("com.example.player"))
    }
}
