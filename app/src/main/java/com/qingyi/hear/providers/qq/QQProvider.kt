package com.qingyi.hear.providers.qq

import com.qingyi.hear.domain.Lyrics
import com.qingyi.hear.domain.Playlist
import com.qingyi.hear.domain.StreamUrl
import com.qingyi.hear.domain.Track
import com.qingyi.hear.network.HearJson
import com.qingyi.hear.network.arr
import com.qingyi.hear.network.asString
import com.qingyi.hear.network.getText
import com.qingyi.hear.network.int
import com.qingyi.hear.network.jsonObjectOrNull
import com.qingyi.hear.network.long
import com.qingyi.hear.network.obj
import com.qingyi.hear.network.string
import com.qingyi.hear.providers.MusicProvider
import com.qingyi.hear.providers.ProviderError
import com.qingyi.hear.storage.CredentialStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class QQProvider(
    private val client: OkHttpClient,
    private val credentials: CredentialStore,
    private val musicuUrl: String = QQ_MUSICU_URL,
    private val playlistListUrl: String = QQ_PLAYLIST_LIST_URL,
    private val playlistDetailUrl: String = QQ_PLAYLIST_DETAIL_URL,
    private val playlistFallbackUrl: String = QQ_PLAYLIST_FALLBACK_URL,
    private val lyricUrl: String = QQ_LYRIC_URL,
) : MusicProvider {
    override val source: String = "qq"
    override val displayName: String = "QQ 音乐"

    override suspend fun search(keyword: String, limit: Int, offset: Int): List<Track> {
        val pageNum = (offset / limit.coerceAtLeast(1)) + 1
        val payload = buildJsonObject {
            put(
                "search",
                buildJsonObject {
                    put("method", "DoSearchForQQMusicDesktop")
                    put("module", "music.search.SearchCgiService")
                    put(
                        "param",
                        buildJsonObject {
                            put("num_per_page", limit.coerceIn(1, 50))
                            put("page_num", pageNum)
                            put("query", keyword)
                            put("search_type", 0)
                        },
                    )
                },
            )
        }
        val url = musicuUrl.toHttpUrl().newBuilder()
            .addQueryParameter("data", HearJson.encodeToString(payload))
            .build()
        val root = requestJson(url.toString(), cookie = credentials.getCookie(source))
        val songs = root.obj("search")
            ?.obj("data")
            ?.obj("body")
            ?.obj("song")
            ?.arr("list")
            ?: JsonArray(emptyList())
        return songs.mapNotNull { item -> item.jsonObjectOrNull()?.let(::trackFromSearchSong) }
    }

    override suspend fun fetchUserPlaylists(): List<Playlist> {
        val cookie = credentials.getCookie(source)
            ?: throw ProviderError("QQ 音乐需要先填写 Cookie")
        val uin = extractUin(cookie)
        val url = playlistListUrl.toHttpUrl().newBuilder()
            .addQueryParameter("hostuin", uin)
            .addQueryParameter("loginUin", uin)
            .addQueryParameter("format", "json")
            .addQueryParameter("inCharset", "utf8")
            .addQueryParameter("outCharset", "utf-8")
            .addQueryParameter("notice", "0")
            .addQueryParameter("platform", "yqq.json")
            .addQueryParameter("needNewCode", "0")
            .addQueryParameter("cid", "205360838")
            .addQueryParameter("sin", "0")
            .addQueryParameter("size", "100")
            .build()
        val root = requestJson(url.toString(), cookie)
        val items = root.obj("data")?.arr("disslist") ?: JsonArray(emptyList())
        return items.mapNotNull { item ->
            item.jsonObjectOrNull()?.let(::playlistFromListItem)
        }.distinctBy { it.id }
    }

    override suspend fun fetchPlaylist(idOrUrl: String): Playlist {
        val playlistId = extractNumericId(idOrUrl)
        val cookie = credentials.getCookie(source)
        val detail = fetchPlaylistRoot(playlistId, cookie)
        val tracks = detail.arr("songlist")
            ?.mapNotNull { item -> item.jsonObjectOrNull()?.let(::trackFromPlaylistSong) }
            ?: emptyList()
        return Playlist(
            id = "qq-$playlistId",
            name = detail.string("dissname") ?: detail.string("diss_name") ?: playlistId,
            kind = source,
            tracks = tracks,
            trackCount = tracks.size,
            description = detail.string("desc"),
            coverUrl = detail.string("logo"),
        )
    }

    override suspend fun resolveStream(track: Track): StreamUrl {
        val identifier = track.resolverId
            ?: throw ProviderError("QQ 音乐歌曲缺少播放解析信息")
        val (songMid, mediaMid) = parseResolverId(identifier)
        val cookie = credentials.getCookie(source)
        val uin = cookie?.let { runCatching { extractUin(it) }.getOrNull() } ?: "0"
        var lastMessage: String? = null

        for (format in qqFormats(hasCookie = !cookie.isNullOrBlank())) {
            val filename = "${format.id}$mediaMid${format.extension}"
            val payload = buildJsonObject {
                put(
                    "comm",
                    buildJsonObject {
                        put("uin", uin)
                        put("format", "json")
                        put("ct", 24)
                        put("cv", 0)
                    },
                )
                put(
                    "req_0",
                    buildJsonObject {
                        put("module", "vkey.GetVkeyServer")
                        put("method", "CgiGetVkey")
                        put(
                            "param",
                            buildJsonObject {
                                put("guid", "10000")
                                put("loginflag", 1)
                                put("filename", buildJsonArray { addString(filename) })
                                put("songmid", buildJsonArray { addString(songMid) })
                                put("songtype", buildJsonArray { addInt(0) })
                                put("uin", uin)
                                put("platform", "20")
                            },
                        )
                    },
                )
            }
            val url = musicuUrl.toHttpUrl().newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("data", HearJson.encodeToString(payload))
                .build()
            val root = requestJson(url.toString(), cookie)
            lastMessage = qqResponseMessage(root) ?: lastMessage
            pickStreamUrl(root)?.let { streamUrl ->
                val headers = mutableMapOf(
                    "Referer" to QQ_REFERER,
                    "User-Agent" to QQ_USER_AGENT,
                )
                if (!cookie.isNullOrBlank()) {
                    headers["Cookie"] = cookie
                }
                return StreamUrl(streamUrl, headers)
            }
        }

        throw ProviderError(
            buildString {
                append("QQ 音乐没有返回可播放链接")
                if (!lastMessage.isNullOrBlank()) {
                    append(": ")
                    append(lastMessage)
                }
            },
        )
    }

    override suspend fun fetchLyrics(track: Track): Lyrics {
        val songMid = qqSongMid(track)
            ?: throw ProviderError("QQ 音乐歌曲缺少歌词解析信息")
        val url = lyricUrl.toHttpUrl().newBuilder()
            .addQueryParameter("songmid", songMid)
            .addQueryParameter("format", "json")
            .addQueryParameter("nobase64", "1")
            .addQueryParameter("g_tk", "5381")
            .addQueryParameter("loginUin", "0")
            .addQueryParameter("hostUin", "0")
            .addQueryParameter("inCharset", "utf8")
            .addQueryParameter("outCharset", "utf-8")
            .addQueryParameter("notice", "0")
            .addQueryParameter("platform", "yqq.json")
            .addQueryParameter("needNewCode", "0")
            .build()
        val root = requestJson(url.toString(), credentials.getCookie(source))
        val lyric = root.string("lyric") ?: root.obj("data")?.string("lyric") ?: ""
        return Lyrics(htmlUnescape(lyric))
    }

    private suspend fun fetchPlaylistRoot(playlistId: String, cookie: String?): JsonObject {
        val primary = playlistDetailUrl.toHttpUrl().newBuilder()
            .addQueryParameter("type", "1")
            .addQueryParameter("json", "1")
            .addQueryParameter("utf8", "1")
            .addQueryParameter("onlysong", "0")
            .addQueryParameter("disstid", playlistId)
            .addQueryParameter("format", "json")
            .addQueryParameter("g_tk", "5381")
            .addQueryParameter("loginUin", "0")
            .addQueryParameter("hostUin", "0")
            .addQueryParameter("inCharset", "utf8")
            .addQueryParameter("outCharset", "utf-8")
            .addQueryParameter("notice", "0")
            .addQueryParameter("platform", "yqq.json")
            .addQueryParameter("needNewCode", "0")
            .build()
        cdList(requestJson(primary.toString(), cookie)).firstOrNull()?.let { return it }

        val fallback = playlistFallbackUrl.toHttpUrl().newBuilder()
            .addQueryParameter("id", playlistId)
            .addQueryParameter("format", "json")
            .addQueryParameter("inCharset", "utf8")
            .addQueryParameter("outCharset", "utf-8")
            .addQueryParameter("notice", "0")
            .addQueryParameter("platform", "yqq.json")
            .addQueryParameter("needNewCode", "0")
            .build()
        return cdList(requestJson(fallback.toString(), cookie)).firstOrNull()
            ?: throw ProviderError("QQ 音乐歌单响应缺少歌曲列表")
    }

    private suspend fun requestJson(url: String, cookie: String?): JsonObject {
        val builder = Request.Builder()
            .url(url)
            .header("Referer", QQ_REFERER)
            .header("Origin", QQ_REFERER)
            .header("User-Agent", QQ_USER_AGENT)
        if (!cookie.isNullOrBlank()) {
            builder.header("Cookie", cookie)
        }
        val text = client.getText(builder.build())
        return HearJson.parseToJsonElement(text).jsonObject
    }

    companion object {
        internal fun extractUin(cookie: String): String {
            val match = Regex("""(?:^|;\s*)(?:uin|qqmusic_uin|p_uin)=o?(\d+)""").find(cookie)
            return match?.groupValues?.get(1)
                ?: throw ProviderError("QQ 音乐 Cookie 缺少 uin")
        }

        internal fun extractNumericId(value: String): String {
            return Regex("""\d+""").findAll(value).lastOrNull()?.value
                ?: throw ProviderError("请输入 QQ 音乐歌单 ID 或链接")
        }

        internal fun qqFormats(hasCookie: Boolean): List<QQFormat> =
            buildList {
                if (hasCookie) add(QQFormat("M800", ".mp3"))
                add(QQFormat("M500", ".mp3"))
                add(QQFormat("C400", ".m4a"))
            }
    }
}

data class QQFormat(val id: String, val extension: String)

private const val QQ_MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
private const val QQ_PLAYLIST_LIST_URL = "https://c.y.qq.com/rsc/fcgi-bin/fcg_user_created_diss"
private const val QQ_PLAYLIST_DETAIL_URL = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg"
private const val QQ_PLAYLIST_FALLBACK_URL = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg"
private const val QQ_LYRIC_URL = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
private const val QQ_REFERER = "https://y.qq.com/"
private const val QQ_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36"

private fun JsonArrayBuilder.addString(value: String) {
    add(JsonPrimitiveCompat(value))
}

private fun JsonArrayBuilder.addInt(value: Int) {
    add(JsonPrimitiveCompat(value))
}

private typealias JsonArrayBuilder = kotlinx.serialization.json.JsonArrayBuilder

private fun JsonPrimitiveCompat(value: String): JsonElement = kotlinx.serialization.json.JsonPrimitive(value)

private fun JsonPrimitiveCompat(value: Int): JsonElement = kotlinx.serialization.json.JsonPrimitive(value)

private fun cdList(root: JsonObject): List<JsonObject> {
    val topLevel = root.arr("cdlist")
    val nested = root.obj("data")?.arr("cdlist")
    return (topLevel ?: nested ?: JsonArray(emptyList()))
        .mapNotNull { it.jsonObjectOrNull() }
}

private fun playlistFromListItem(item: JsonObject): Playlist? {
    val id = qqPlaylistId(item)
    if (id.isBlank()) return null
    val count = item.int("song_cnt") ?: item.int("songnum") ?: item.int("total_song_num")
    return Playlist(
        id = "qq-$id",
        name = item.string("dissname") ?: item.string("diss_name") ?: item.string("title") ?: item.string("name") ?: id,
        kind = "qq",
        trackCount = count,
        coverUrl = item.string("logo") ?: item.string("picurl"),
    )
}

private fun qqPlaylistId(item: JsonObject): String =
    item.string("disstid")
        ?: item.string("dissid")
        ?: item.string("tid")
        ?: item.string("dirid")
        ?: item.string("id")
        ?: item.string("diss_id")
        ?: ""

private fun trackFromSearchSong(song: JsonObject): Track {
    val album = song.obj("album")
    val albumMid = album?.string("mid")
    val mid = song.string("mid") ?: song.string("songmid") ?: song.string("strMediaMid") ?: ""
    return Track(
        source = "qq",
        id = song.string("id") ?: song.long("id")?.toString() ?: mid,
        title = song.string("name") ?: "未命名歌曲",
        artists = song.arr("singer").orEmptyObjects().mapNotNull { it.string("name") },
        album = album?.string("name"),
        durationMs = song.long("interval")?.times(1000),
        coverUrl = albumMid?.takeIf { it.isNotBlank() }
            ?.let { "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg" },
        resolverId = if (mid.isNotBlank()) "$mid:::$mid" else null,
        raw = song,
    )
}

private fun trackFromPlaylistSong(song: JsonObject): Track {
    val title = song.string("songname") ?: song.string("songorig") ?: song.string("name") ?: "未命名歌曲"
    val songMid = song.string("songmid") ?: song.string("mid") ?: song.string("strMediaMid") ?: ""
    val mediaMid = song.string("media_mid") ?: song.string("strMediaMid") ?: songMid
    val albumObject = song.obj("album")
    val albumMid = song.string("albummid") ?: albumObject?.string("mid")
    val duration = song.long("interval")?.takeIf { it > 0 }?.times(1000)
    return Track(
        source = "qq",
        id = song.string("songid") ?: song.long("songid")?.toString() ?: songMid,
        title = title,
        artists = song.arr("singer").orEmptyObjects().mapNotNull { it.string("name") },
        album = song.string("albumname") ?: albumObject?.string("name"),
        durationMs = duration,
        coverUrl = albumMid?.takeIf { it.isNotBlank() }
            ?.let { "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg" },
        resolverId = if (songMid.isNotBlank() && mediaMid.isNotBlank()) "$songMid:::$mediaMid" else null,
        raw = song,
    )
}

private fun JsonArray?.orEmptyObjects(): List<JsonObject> =
    this?.mapNotNull { it.jsonObjectOrNull() } ?: emptyList()

private fun parseResolverId(identifier: String): Pair<String, String> {
    val parts = identifier.split(":::", limit = 2)
    if (parts.size != 2 || parts.any { it.isBlank() }) {
        throw ProviderError("QQ 音乐播放解析信息格式不正确")
    }
    return parts[0] to parts[1]
}

private fun pickStreamUrl(root: JsonObject): String? {
    val data = root.obj("req_0")?.obj("data") ?: return null
    val server = data.arr("sip")?.firstNotNullOfOrNull { it.asString()?.takeIf(String::isNotBlank) }.orEmpty()
    val purl = data.arr("midurlinfo")
        ?.firstNotNullOfOrNull { it.jsonObjectOrNull()?.string("purl")?.takeIf(String::isNotBlank) }
        ?: return null
    return if (purl.startsWith("http://") || purl.startsWith("https://")) {
        purl
    } else {
        server.takeIf { it.isNotBlank() }?.let { "$it$purl" }
    }
}

private fun qqResponseMessage(root: JsonObject): String? {
    return root.obj("req_0")
        ?.obj("data")
        ?.arr("midurlinfo")
        ?.firstNotNullOfOrNull { it.jsonObjectOrNull()?.string("tips")?.takeIf(String::isNotBlank) }
}

private fun qqSongMid(track: Track): String? {
    track.resolverId?.takeIf { it.contains(":::") }?.let { return it.substringBefore(":::") }
    return track.raw["songmid"].asString()
        ?: track.raw["mid"].asString()
        ?: track.raw["strMediaMid"].asString()
}

private fun htmlUnescape(value: String): String =
    value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
