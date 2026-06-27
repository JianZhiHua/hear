package com.qingyi.hear.core.search

import com.qingyi.hear.core.network.asArr
import com.qingyi.hear.core.network.asObj
import com.qingyi.hear.core.network.long
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

/**
 * QQ音乐搜索数据源。
 *
 * 使用 `u.y.qq.com` 的 `musicu.fcg` 公开 JSON 接口，匿名调用
 * `music.search.SearchCgiService.DoSearchForQQMusicDesktop`，
 * 不携带 skey / uin 等任何登录凭据。
 */
class QQMusicSearchDataSource(
    private val client: HttpClient,
    private val json: Json,
) : MusicSearchDataSource {
    companion object {
        private const val SEARCH_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        private const val REFERER = "https://y.qq.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    /**
     * 匿名搜索。
     * @param keyword 关键词
     * @param limit   返回条数上限
     */
    override suspend fun search(keyword: String, limit: Int): List<MusicSearchResult> {
        return runCatching {
            val response: HttpResponse = client.post(SEARCH_URL) {
                contentType(ContentType.Application.Json)
                header("Referer", REFERER)
                header("User-Agent", USER_AGENT)
                setBody(buildSearchBody(keyword, limit))
            }
            val body = json.parseToJsonElement(response.body<String>()).asObj() ?: return emptyList()
            // 真实接口返回 req_1.data.body.song.list
            val list = body["req_1"]?.asObj()
                ?.get("data")?.asObj()
                ?.get("body")?.asObj()
                ?.get("song")?.asObj()
                ?.get("list")?.asArr() ?: return emptyList()
            list.mapNotNull { it.asObj()?.toResult() }
        }.getOrDefault(emptyList())
    }

    private fun buildSearchBody(keyword: String, limit: Int): JsonObject = buildJsonObject {
        putJsonObject("req_1") {
            put("module", "music.search.SearchCgiService")
            put("method", "DoSearchForQQMusicDesktop")
            putJsonObject("param") {
                put("query", keyword)
                put("page_num", 1)
                put("page_size", limit)
            }
        }
    }

    private fun JsonObject.toResult(): MusicSearchResult? {
        // songmid 优先作为 songId；缺失时回退到数字 id
        val mid = this["mid"]?.str() ?: this["songmid"]?.str() ?: this["id"]?.str() ?: return null
        val name = this["name"]?.str() ?: this["title"]?.str() ?: return null
        val singers = this["singer"]?.asArr()
        val artist = singers
            ?.joinToString("/") { it.asObj()?.get("name").str().orEmpty() }
            .orEmpty()
        val album = this["album"]?.asObj()?.get("name").str()
        // QQ 音乐 interval 单位为秒，转换为毫秒
        val durationSec = this["interval"]?.long()
        val duration = durationSec?.let { it * 1000L }
        return MusicSearchResult(
            title = name,
            artist = artist,
            album = album,
            duration = duration,
            source = MusicSource.QQ_MUSIC,
            songId = mid,
        )
    }
}
