package com.qingyi.hear.providers.netease

import com.qingyi.hear.domain.AudioQuality
import com.qingyi.hear.domain.Track
import com.qingyi.hear.providers.MemoryCredentialStore
import com.qingyi.hear.providers.ProviderError
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class NetEaseResolveStreamTest {

    private fun track(id: String = "1001") = Track(
        source = "netease",
        id = id,
        title = "Test Track",
        artists = listOf("Artist"),
        resolverId = id,
    )

    /** 返回一个带 URL 的成功响应 */
    private fun successResponse(url: String = "https://stream.test/song.mp3") =
        MockResponse.Builder()
            .body(
                """
                {
                  "data": [
                    {
                      "url": "$url",
                      "type": "mp3",
                      "size": 1024000
                    }
                  ]
                }
                """.trimIndent(),
            )
            .build()

    /** 返回空 URL 的响应（模拟音质不支持） */
    private fun emptyUrlResponse() =
        MockResponse.Builder()
            .body(
                """
                {
                  "data": [
                    {
                      "url": null,
                      "type": null,
                      "size": 0
                    }
                  ]
                }
                """.trimIndent(),
            )
            .build()

    @Test
    fun standardQualityReturnsSuccess() = runTest {
        val server = MockWebServer()
        server.enqueue(successResponse())
        server.start()

        server.use {
            val provider = NetEaseProvider(
                client = OkHttpClient(),
                credentials = MemoryCredentialStore(),
                domain = server.url("").toString().trimEnd('/'),
                apiDomain = server.url("").toString().trimEnd('/'),
            )

            val result = provider.resolveStream(track(), AudioQuality.Standard)
            assertEquals("https://stream.test/song.mp3", result.url)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun exHighQualityReturnsSuccess() = runTest {
        val server = MockWebServer()
        server.enqueue(successResponse())
        server.start()

        server.use {
            val provider = NetEaseProvider(
                client = OkHttpClient(),
                credentials = MemoryCredentialStore(),
                domain = server.url("").toString().trimEnd('/'),
                apiDomain = server.url("").toString().trimEnd('/'),
            )

            val result = provider.resolveStream(track(), AudioQuality.ExHigh)
            assertEquals("https://stream.test/song.mp3", result.url)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun losslessQualityReturnsSuccess() = runTest {
        val server = MockWebServer()
        server.enqueue(successResponse("https://stream.test/song.flac"))
        server.start()

        server.use {
            val provider = NetEaseProvider(
                client = OkHttpClient(),
                credentials = MemoryCredentialStore(),
                domain = server.url("").toString().trimEnd('/'),
                apiDomain = server.url("").toString().trimEnd('/'),
            )

            val result = provider.resolveStream(track(), AudioQuality.Lossless)
            assertEquals("https://stream.test/song.flac", result.url)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun losslessFallsBackToExHighWhenUnsupported() = runTest {
        val server = MockWebServer()
        server.enqueue(emptyUrlResponse())   // Lossless 失败
        server.enqueue(successResponse())     // ExHigh 成功
        server.start()

        server.use {
            val provider = NetEaseProvider(
                client = OkHttpClient(),
                credentials = MemoryCredentialStore(),
                domain = server.url("").toString().trimEnd('/'),
                apiDomain = server.url("").toString().trimEnd('/'),
            )

            val result = provider.resolveStream(track(), AudioQuality.Lossless)
            assertEquals("https://stream.test/song.mp3", result.url)
            assertEquals(2, server.requestCount) // 降级了一次
        }
    }

    @Test
    fun fallsBackThroughAllLevels() = runTest {
        val server = MockWebServer()
        server.enqueue(emptyUrlResponse())   // Lossless 失败
        server.enqueue(emptyUrlResponse())   // ExHigh 失败
        server.enqueue(successResponse())     // Standard 成功
        server.start()

        server.use {
            val provider = NetEaseProvider(
                client = OkHttpClient(),
                credentials = MemoryCredentialStore(),
                domain = server.url("").toString().trimEnd('/'),
                apiDomain = server.url("").toString().trimEnd('/'),
            )

            val result = provider.resolveStream(track(), AudioQuality.Lossless)
            assertEquals("https://stream.test/song.mp3", result.url)
            assertEquals(3, server.requestCount) // 降级了两次
        }
    }

    @Test(expected = ProviderError::class)
    fun throwsWhenAllQualitiesFail() = runTest {
        val server = MockWebServer()
        server.enqueue(emptyUrlResponse())   // Lossless 失败
        server.enqueue(emptyUrlResponse())   // ExHigh 失败
        server.enqueue(emptyUrlResponse())   // Standard 失败
        server.start()

        server.use {
            val provider = NetEaseProvider(
                client = OkHttpClient(),
                credentials = MemoryCredentialStore(),
                domain = server.url("").toString().trimEnd('/'),
                apiDomain = server.url("").toString().trimEnd('/'),
            )

            provider.resolveStream(track(), AudioQuality.Lossless)
            // 应该抛出 ProviderError
        }
    }

    @Test
    fun standardHasNoFallbackAndThrowsImmediately() = runTest {
        val server = MockWebServer()
        server.enqueue(emptyUrlResponse())   // Standard 失败，无降级
        server.start()

        server.use {
            val provider = NetEaseProvider(
                client = OkHttpClient(),
                credentials = MemoryCredentialStore(),
                domain = server.url("").toString().trimEnd('/'),
                apiDomain = server.url("").toString().trimEnd('/'),
            )

            try {
                provider.resolveStream(track(), AudioQuality.Standard)
                throw AssertionError("Expected ProviderError")
            } catch (e: ProviderError) {
                assertEquals(1, server.requestCount) // 只请求了一次
            }
        }
    }

    @Test
    fun exHighFallsBackToStandardOnly() = runTest {
        val server = MockWebServer()
        server.enqueue(emptyUrlResponse())   // ExHigh 失败
        server.enqueue(successResponse())     // Standard 成功
        server.start()

        server.use {
            val provider = NetEaseProvider(
                client = OkHttpClient(),
                credentials = MemoryCredentialStore(),
                domain = server.url("").toString().trimEnd('/'),
                apiDomain = server.url("").toString().trimEnd('/'),
            )

            val result = provider.resolveStream(track(), AudioQuality.ExHigh)
            assertEquals("https://stream.test/song.mp3", result.url)
            assertEquals(2, server.requestCount)
        }
    }
}
