package com.qingyi.hear.core.lyrics

import com.qingyi.hear.core.network.asObj
import com.qingyi.hear.core.network.str
import com.qingyi.hear.domain.MusicSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/**
 * QQ音乐歌词抓取器。
 *
 * 使用 `u.y.qq.com` 的 `musicu.fcg` 公开 JSON 接口，匿名调用
 * `music.musichallSong.PlayLyricInfo.GetPlayLyricInfo`，不携带 skey / uin。
 * 返回的 `lyric` 字段为 Base64 编码的 LRC 文本，解码后交由 [LyricsParser] 解析。
 */
class QQLyricsFetcher(
    private val client: HttpClient,
    private val json: Json,
) : LyricsFetcher {

    companion object {
        private const val LYRIC_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private const val REFERER = "https://y.qq.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    override suspend fun fetch(songId: String): Lyrics? = runCatching {
        val response: HttpResponse = client.post(LYRIC_URL) {
            contentType(ContentType.Application.Json)
            header("Referer", REFERER)
            header("User-Agent", USER_AGENT)
            setBody(buildBody(songId))
        }
        val body = json.parseToJsonElement(response.body<String>()).asObj() ?: return null
        val data = body["req_1"]?.asObj()?.get("data")?.asObj() ?: return null
        // 优先 songmid；部分场景返回 Base64 编码的 LRC
        val raw = data["lyric"].str().orEmpty()
        if (raw.isBlank()) return null
        val lrc = decodeLyric(raw)
        if (lrc.isBlank()) return null
        val lines = LyricsParser.parseLrc(lrc)
        if (lines.isEmpty()) return null
        Lyrics(lines = lines, source = MusicSource.QQ_MUSIC)
    }.getOrNull()

    private fun buildBody(songMid: String): JsonObject = buildJsonObject {
        putJsonObject("req_1") {
            put("module", "music.musichallSong.PlayLyricInfo")
            put("method", "GetPlayLyricInfo")
            putJsonObject("param") {
                put("songMID", songMid)
                put("songID", 0)
            }
        }
    }

    /** 尝试 Base64 解码；若解码结果非文本歌词则视作明文 LRC。 */
    private fun decodeLyric(raw: String): String {
        return runCatching {
            String(Base64.getDecoder().decode(raw), Charsets.UTF_8)
        }.getOrElse { raw }
    }
}
