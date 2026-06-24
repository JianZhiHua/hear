package com.qingyi.hear.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioQualityTest {

    @Test
    fun standardUsesMp3EncodeType() {
        assertEquals("mp3", AudioQuality.Standard.netEaseEncodeType)
        assertEquals("standard", AudioQuality.Standard.netEaseLevel)
    }

    @Test
    fun exHighUsesMp3EncodeType() {
        assertEquals("mp3", AudioQuality.ExHigh.netEaseEncodeType)
        assertEquals("exhigh", AudioQuality.ExHigh.netEaseLevel)
    }

    @Test
    fun losslessUsesFlacEncodeType() {
        assertEquals("flac", AudioQuality.Lossless.netEaseEncodeType)
        assertEquals("lossless", AudioQuality.Lossless.netEaseLevel)
    }

    @Test
    fun displayNamesAreCorrect() {
        assertEquals("标准", AudioQuality.Standard.displayName)
        assertEquals("高品", AudioQuality.ExHigh.displayName)
        assertEquals("无损", AudioQuality.Lossless.displayName)
    }

    @Test
    fun losslessFallsBackToExHigh() {
        assertEquals(AudioQuality.ExHigh, AudioQuality.Lossless.fallback())
    }

    @Test
    fun exHighFallsBackToStandard() {
        assertEquals(AudioQuality.Standard, AudioQuality.ExHigh.fallback())
    }

    @Test
    fun standardHasNoFallback() {
        assertNull(AudioQuality.Standard.fallback())
    }

    @Test
    fun fullFallbackChainIsThreeLevels() {
        // Lossless -> ExHigh -> Standard -> null
        assertEquals(AudioQuality.ExHigh, AudioQuality.Lossless.fallback())
        assertEquals(AudioQuality.Standard, AudioQuality.Lossless.fallback()?.fallback())
        assertNull(AudioQuality.Lossless.fallback()?.fallback()?.fallback())
    }

    @Test
    fun encodeTypeMatchesExpectedPerQuality() {
        // 核心修复验证：standard/exhigh 必须是 mp3，lossless 必须是 flac
        // 之前硬编码为 flac 导致 standard/exhigh 请求返回空 URL
        for (quality in AudioQuality.entries) {
            val expected = when (quality) {
                AudioQuality.Standard, AudioQuality.ExHigh -> "mp3"
                AudioQuality.Lossless -> "flac"
            }
            assertEquals(
                "${quality.name} encodeType should be $expected",
                expected,
                quality.netEaseEncodeType,
            )
        }
    }
}
