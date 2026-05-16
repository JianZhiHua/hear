package com.qingyi.hear.providers.netease

import com.qingyi.hear.domain.AudioQuality
import com.qingyi.hear.domain.Lyrics
import com.qingyi.hear.domain.Playlist
import com.qingyi.hear.domain.StreamUrl
import com.qingyi.hear.domain.Track
import com.qingyi.hear.network.HearJson
import com.qingyi.hear.network.arr
import com.qingyi.hear.network.asInt
import com.qingyi.hear.network.asLong
import com.qingyi.hear.network.asString
import com.qingyi.hear.network.await
import com.qingyi.hear.network.jsonObjectOrNull
import com.qingyi.hear.network.long
import com.qingyi.hear.network.obj
import com.qingyi.hear.network.string
import com.qingyi.hear.providers.MusicProvider
import com.qingyi.hear.providers.ProviderError
import com.qingyi.hear.storage.CredentialStore
import java.io.IOException
import java.net.URLEncoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class NetEaseProvider(
    private val client: OkHttpClient,
    private val credentials: CredentialStore,
    private val domain: String = NETEASE_DOMAIN,
    private val apiDomain: String = NETEASE_API_DOMAIN,
) : MusicProvider {
    override val source: String = "netease"
    override val displayName: String = "网易云音乐"

    override suspend fun search(keyword: String, limit: Int): List<Track> {
        val root = request(
            uri = "/api/search/get",
            data = buildJsonObject {
                put("s", keyword)
                put("type", 1)
                put("limit", limit.coerceIn(1, 50))
                put("offset", 0)
            },
            crypto = CryptoMode.Eapi,
        )
        val songs = root.obj("result")?.arr("songs") ?: JsonArray(emptyList())
        return songs.mapNotNull { it.jsonObjectOrNull()?.let(::trackFromPayload) }
    }

    override suspend fun fetchUserPlaylists(): List<Playlist> {
        val cookie = requireCookie()
        val account = request(
            uri = "/api/nuser/account/get",
            data = buildJsonObject {},
            crypto = CryptoMode.Weapi,
            cookieOverride = cookie,
        )
        val userId = account.obj("profile")?.long("userId")
            ?: account.obj("account")?.long("id")
            ?: throw ProviderError("网易云音乐没有返回用户 ID")
        val root = request(
            uri = "/api/user/playlist",
            data = buildJsonObject {
                put("uid", userId)
                put("limit", 1000)
                put("offset", 0)
                put("includeVideo", true)
            },
            crypto = CryptoMode.Weapi,
            cookieOverride = cookie,
        )
        return root.arr("playlist")
            ?.mapNotNull { it.jsonObjectOrNull()?.let(::playlistSummaryFromPayload) }
            ?: emptyList()
    }

    override suspend fun fetchPlaylist(idOrUrl: String): Playlist {
        val playlistId = extractNumericId(idOrUrl)
        val root = request(
            uri = "/api/v6/playlist/detail",
            data = buildJsonObject {
                put("id", playlistId)
                put("n", 100000)
                put("s", 8)
            },
            crypto = CryptoMode.Eapi,
        )
        val playlist = root.obj("playlist")
            ?: throw ProviderError("NetEase playlist was not returned")
        return playlistFromPayload(playlist)
    }

    override suspend fun resolveStream(track: Track, quality: AudioQuality): StreamUrl {
        val cookie = credentials.getCookie(source)
        val id = track.resolverId ?: track.id
        val root = request(
            uri = "/api/song/enhance/player/url/v1",
            data = buildJsonObject {
                put("ids", "[$id]")
                put("level", quality.netEaseLevel)
                put("encodeType", "flac")
            },
            crypto = CryptoMode.Eapi,
            cookieOverride = cookie,
        )
        val item = root.arr("data")?.firstOrNull()?.jsonObjectOrNull()
        val url = item?.string("url")
        if (url.isNullOrBlank()) {
            throw ProviderError(describeStreamFailure(root, item, quality.netEaseLevel))
        }
        val headers = mutableMapOf(
            "Referer" to NETEASE_DOMAIN,
            "User-Agent" to NETEASE_USER_AGENT,
        )
        if (!cookie.isNullOrBlank()) {
            headers["Cookie"] = cookie
        }
        return StreamUrl(url, headers)
    }

    override suspend fun fetchLyrics(track: Track): Lyrics {
        val root = request(
            uri = "/api/song/lyric/v1",
            data = buildJsonObject {
                put("id", track.resolverId ?: track.id)
                put("cp", false)
                put("tv", 0)
                put("lv", 0)
                put("rv", 0)
                put("kv", 0)
                put("yv", 0)
                put("ytv", 0)
                put("yrv", 0)
            },
            crypto = CryptoMode.Eapi,
        )
        return Lyrics(
            text = root.obj("lrc")?.string("lyric") ?: root.string("lyric") ?: "",
            translatedText = root.obj("tlyric")?.string("lyric"),
        )
    }

    private suspend fun request(
        uri: String,
        data: JsonObject,
        crypto: CryptoMode,
        cookieOverride: String? = credentials.getCookie(source),
    ): JsonObject {
        val cookie = cookieOverride.orEmpty()
        val cookieMap = parseCookie(cookie)
        val headers = mutableMapOf<String, String>()
        val encrypted: Map<String, String>
        val url: String

        when (crypto) {
            CryptoMode.Weapi -> {
                val csrf = cookieMap["__csrf"].orEmpty()
                val payload = copyJsonObject(data) {
                    put("csrf_token", csrf)
                }
                encrypted = NetEaseCrypto.weapi(payload)
                headers["Referer"] = domain
                headers["User-Agent"] = NETEASE_WEB_USER_AGENT
                if (cookie.isNotBlank()) headers["Cookie"] = cookie
                url = "$domain/weapi/${uri.removePrefix("/api/")}"
            }

            CryptoMode.Eapi -> {
                val header = netEaseHeader(cookieMap)
                val payload = copyJsonObject(data) {
                    put("header", header)
                }
                encrypted = NetEaseCrypto.eapi(uri, payload)
                headers["Cookie"] = headerCookie(header)
                headers["User-Agent"] = NETEASE_USER_AGENT
                url = "$apiDomain/eapi/${uri.removePrefix("/api/")}"
            }
        }

        val form = FormBody.Builder().apply {
            encrypted.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .post(form)
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
        val response = client.await(request)
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) {
                throw IOException("HTTP ${it.code}: ${it.message}")
            }
            return HearJson.parseToJsonElement(text).jsonObject
        }
    }

    private fun requireCookie(): String =
        credentials.getCookie(source)?.takeIf { it.isNotBlank() }
            ?: throw ProviderError("网易云音乐需要先填写 Cookie")
}

enum class CryptoMode {
    Weapi,
    Eapi,
}

private const val NETEASE_DOMAIN = "https://music.163.com"
private const val NETEASE_API_DOMAIN = "https://interface.music.163.com"
private const val NETEASE_USER_AGENT =
    "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
private const val NETEASE_WEB_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"

private fun copyJsonObject(source: JsonObject, add: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
    buildJsonObject {
        source.forEach { (key, value) -> put(key, value) }
        add()
    }

private fun parseCookie(cookie: String): Map<String, String> =
    cookie.split(";")
        .mapNotNull { segment ->
            val parts = segment.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
        }
        .toMap()

private fun netEaseHeader(cookie: Map<String, String>): JsonObject =
    buildJsonObject {
        put("osver", cookie["osver"] ?: "16.2")
        put("deviceId", cookie["deviceId"] ?: "")
        put("os", cookie["os"] ?: "iPhone OS")
        put("appver", cookie["appver"] ?: "9.0.90")
        put("versioncode", cookie["versioncode"] ?: "140")
        put("mobilename", cookie["mobilename"] ?: "")
        put("buildver", cookie["buildver"] ?: (System.currentTimeMillis() / 1000).toString())
        put("resolution", cookie["resolution"] ?: "1920x1080")
        put("__csrf", cookie["__csrf"] ?: "")
        put("channel", cookie["channel"] ?: "distribution")
        put("requestId", "${System.currentTimeMillis()}_${(0..9999).random().toString().padStart(4, '0')}")
        cookie["MUSIC_U"]?.let { put("MUSIC_U", it) }
        cookie["MUSIC_A"]?.let { put("MUSIC_A", it) }
    }

private fun headerCookie(header: JsonObject): String =
    header.entries.joinToString("; ") { (key, value) ->
        val text = value.asString().orEmpty()
        "${urlEncode(key)}=${urlEncode(text)}"
    }

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

private fun trackFromPayload(data: JsonObject): Track {
    val artists = (data.arr("ar") ?: data.arr("artists"))
        ?.mapNotNull { it.jsonObjectOrNull()?.string("name") }
        ?: emptyList()
    val album = data.obj("al") ?: data.obj("album")
    return Track(
        source = "netease",
        id = data.string("id") ?: data.long("id")?.toString() ?: "",
        title = data.string("name") ?: "未命名歌曲",
        artists = artists,
        album = album?.string("name"),
        durationMs = data.long("dt") ?: data.long("duration"),
        coverUrl = album?.string("picUrl"),
        resolverId = data.string("id") ?: data.long("id")?.toString(),
        raw = data,
    )
}

private fun playlistSummaryFromPayload(data: JsonObject): Playlist =
    Playlist(
        id = "netease-${data.string("id") ?: data.long("id")}",
        name = data.string("name") ?: data.string("id") ?: "未命名歌单",
        kind = "netease",
        trackCount = data.long("trackCount")?.toInt(),
        coverUrl = data.string("coverImgUrl"),
        description = data.string("description"),
    )

private fun playlistFromPayload(data: JsonObject): Playlist {
    val id = data.string("id") ?: data.long("id")?.toString() ?: ""
    val tracks = data.arr("tracks")
        ?.mapNotNull { it.jsonObjectOrNull()?.let(::trackFromPayload) }
        ?: emptyList()
    return Playlist(
        id = "netease-$id",
        name = data.string("name") ?: id,
        kind = "netease",
        tracks = tracks,
        trackCount = data.long("trackCount")?.toInt() ?: tracks.size,
        coverUrl = data.string("coverImgUrl"),
        description = data.string("description"),
    )
}

private fun extractNumericId(value: String): String =
    Regex("""\d+""").findAll(value).lastOrNull()?.value
        ?: throw ProviderError("请输入网易云音乐歌单 ID 或链接")

private fun describeStreamFailure(root: JsonObject, item: JsonObject?, requestedLevel: String): String {
    val details = mutableListOf("网易云音乐没有返回可播放链接", "音质=$requestedLevel")
    val code = item?.get("code")?.asInt() ?: root["code"].asInt()
    if (code != null) details += "code=$code"
    item?.string("level")?.let { details += "返回音质=$it" }
    item?.string("type")?.let { details += "格式=$it" }
    val message = item?.string("message") ?: item?.string("msg") ?: root.string("message") ?: root.string("msg")
    if (!message.isNullOrBlank()) details += message
    return details.joinToString("; ")
}
