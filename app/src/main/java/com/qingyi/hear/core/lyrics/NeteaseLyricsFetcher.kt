package com.qingyi.hear.core.lyrics

import com.qingyi.hear.core.network.asObj
import com.qingyi.hear.core.network.str
import com.qingyi.hear.domain.MusicSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.Json

/**
 * 网易云歌词抓取器。
 *
 * 使用网易云公开的非登录歌词接口 `/api/song/lyric`，不携带 cookie。
 * 返回的 `lrc.lyric` 为标准 LRC 文本，交由 [LyricsParser] 解析。
 */
class NeteaseLyricsFetcher(
    private val client: HttpClient,
    private val json: Json,
) : LyricsFetcher {

    companion object {
        private const val LYRIC_URL = "https://music.163.com/api/song/lyric"
        private const val REFERER = "https://music.163.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    override suspend fun fetch(songId: String): Lyrics? = runCatching {
        val response: HttpResponse = client.get(LYRIC_URL) {
            url {
                parameters.append("os", "pc")
                parameters.append("id", songId)
                parameters.append("lv", "-1")
                parameters.append("kv", "-1")
                parameters.append("tv", "-1")
            }
            header("Referer", REFERER)
            header("User-Agent", USER_AGENT)
        }
        val body = json.parseToJsonElement(response.body<String>()).asObj() ?: return null
        val lrc = body["lrc"]?.asObj()?.get("lyric").str()
        if (lrc.isNullOrBlank()) return null
        val lines = LyricsParser.parseLrc(lrc)
        if (lines.isEmpty()) return null
        Lyrics(lines = lines, source = MusicSource.NETEASE_CLOUD)
    }.getOrNull()
}
