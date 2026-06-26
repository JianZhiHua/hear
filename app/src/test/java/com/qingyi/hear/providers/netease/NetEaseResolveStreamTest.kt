package com.qingyi.hear.providers.netease

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
    fun resolveStreamReturnsSuccess() = runTest {
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

            val result = provider.resolveStream(track())
            assertEquals("https://stream.test/song.mp3", result.url)
            assertEquals(1, server.requestCount)
        }
    }

    @Test(expected = ProviderError::class)
    fun throwsWhenNoUrlReturned() = runTest {
        val server = MockWebServer()
        server.enqueue(emptyUrlResponse())
        server.start()

        server.use {
            val provider = NetEaseProvider(
                client = OkHttpClient(),
                credentials = MemoryCredentialStore(),
                domain = server.url("").toString().trimEnd('/'),
                apiDomain = server.url("").toString().trimEnd('/'),
            )

            provider.resolveStream(track())
        }
    }
}
