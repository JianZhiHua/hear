package com.qingyi.hear.domain

import com.qingyi.hear.network.HearJson
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricSettingsSerializationTest {
    @Test
    fun lyricSettingsRoundTripThroughJson() {
        val settings = LyricSettings(
            fontSizeSp = 22f,
            color = LyricColor.Green,
            alignment = LyricTextAlign.End,
            lineSpacing = 1.7f,
            backgroundStyle = LyricBackgroundStyle.Dark,
        )

        val payload = HearJson.encodeToString(LyricSettings.serializer(), settings)
        val restored = HearJson.decodeFromString(LyricSettings.serializer(), payload)

        assertEquals(settings, restored)
    }
}
