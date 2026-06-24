package com.qingyi.hear.providers

import com.qingyi.hear.providers.netease.NetEaseProvider
import com.qingyi.hear.providers.netease.NetEaseCrypto
import com.qingyi.hear.providers.qq.QQProvider
import com.qingyi.hear.storage.CredentialStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLogicTest {
    @Test
    fun qqCookieUinExtractionAcceptsCommonKeys() {
        assertEquals("123456", QQProvider.extractUin("foo=bar; uin=o123456; baz=1"))
        assertEquals("789", QQProvider.extractUin("qqmusic_uin=789; foo=bar"))
        assertEquals("2468", QQProvider.extractUin("p_uin=o2468"))
    }

    @Test
    fun qqFormatFallbackMatchesPlan() {
        assertEquals(listOf("M800", "M500", "C400"), QQProvider.qqFormats(hasCookie = true).map { it.id })
        assertEquals(listOf("M500", "C400"), QQProvider.qqFormats(hasCookie = false).map { it.id })
    }

    @Test
    fun netEaseWeapiProducesExpectedShape() {
        val encrypted = NetEaseCrypto.weapi(
            data = buildJsonObject {
                put("s", "hello")
                put("type", 1)
            },
            secretKey = "abcdefghijklmnop",
        )

        assertTrue(encrypted.getValue("params").isNotBlank())
        assertEquals(256, encrypted.getValue("encSecKey").length)
        assertTrue(encrypted.getValue("encSecKey").all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun netEaseEapiProducesUppercaseHexParams() {
        val encrypted = NetEaseCrypto.eapi(
            url = "/api/song/enhance/player/url/v1",
            data = buildJsonObject {
                put("ids", "[1]")
                put("level", "exhigh")
            },
        )

        val params = encrypted.getValue("params")
        assertFalse(params.isBlank())
        assertTrue(params.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun qqSearchMapsTracksThroughHttpClient() = runTest {
        val server = MockWebServer()
        val credentials = MemoryCredentialStore().apply {
            setCookie("qq", "uin=o123456; foo=bar")
        }
        server.enqueue(
            MockResponse.Builder()
                .body(
                    """
                    {
                      "search": {
                        "data": {
                          "body": {
                            "song": {
                              "list": [
                                {
                                  "id": 1,
                                  "name": "Track A",
                                  "mid": "songmid",
                                  "interval": 180,
                                  "album": {"mid": "albummid", "name": "Album A"},
                                  "singer": [{"name": "Artist A"}]
                                }
                              ]
                            }
                          }
                        }
                      }
                    }
                    """.trimIndent(),
                )
                .build(),
        )
        server.start()

        server.use {
            val provider = QQProvider(
                client = OkHttpClient(),
                credentials = credentials,
                musicuUrl = server.url("/musicu").toString(),
            )

            val tracks = provider.search("Track", limit = 5)
            val request = server.takeRequest()

            assertEquals("Track A", tracks.single().title)
            assertEquals("Artist A", tracks.single().artists.single())
            assertEquals("songmid:::songmid", tracks.single().resolverId)
            assertEquals("uin=o123456; foo=bar", request.headers["Cookie"])
            assertTrue(request.url.queryParameter("data")!!.contains("DoSearchForQQMusicDesktop"))
        }
    }

    @Test
    fun qqUserPlaylistsSkipsEmptyIdsAndDeduplicates() = runTest {
        val server = MockWebServer()
        val credentials = MemoryCredentialStore().apply {
            setCookie("qq", "uin=o123456; foo=bar")
        }
        server.enqueue(
            MockResponse.Builder()
                .body(
                    """
                    {
                      "data": {
                        "disslist": [
                          {"dissname": "No Id"},
                          {"disstid": "100", "dissname": "Valid", "song_cnt": 3},
                          {"disstid": "100", "dissname": "Duplicate", "song_cnt": 5}
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                .build(),
        )
        server.start()

        server.use {
            val provider = QQProvider(
                client = OkHttpClient(),
                credentials = credentials,
                playlistListUrl = server.url("/playlist-list").toString(),
            )

            val playlists = provider.fetchUserPlaylists()

            assertEquals(1, playlists.size)
            assertEquals("qq-100", playlists.single().id)
            assertEquals("Valid", playlists.single().name)
        }
    }

    @Test
    fun netEaseSearchPostsEncryptedPayloadAndMapsTracks() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse.Builder()
                .body(
                    """
                    {
                      "result": {
                        "songs": [
                          {
                            "id": 10,
                            "name": "NetEase Track",
                            "dt": 210000,
                            "ar": [{"name": "NetEase Artist"}],
                            "al": {"name": "NetEase Album", "picUrl": "https://img.test/a.jpg"}
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                .build(),
        )
        server.start()

        server.use {
            val baseUrl = server.url("").toString().trimEnd('/')
            val provider = NetEaseProvider(
                client = OkHttpClient(),
                credentials = MemoryCredentialStore(),
                domain = baseUrl,
                apiDomain = baseUrl,
            )

            val tracks = provider.search("NetEase", limit = 1)
            val request = server.takeRequest()

            assertEquals("/eapi/search/get", request.url.encodedPath)
            assertTrue(request.body?.utf8().orEmpty().contains("params="))
            assertEquals("NetEase Track", tracks.single().title)
            assertEquals("NetEase Artist", tracks.single().artists.single())
        }
    }
}

internal class MemoryCredentialStore : CredentialStore {
    private val cookies = mutableMapOf<String, String>()

    override fun getCookie(source: String): String? = cookies[source]

    override fun setCookie(source: String, cookie: String) {
        cookies[source] = cookie
    }

    override fun clearCookie(source: String) {
        cookies.remove(source)
    }
}
