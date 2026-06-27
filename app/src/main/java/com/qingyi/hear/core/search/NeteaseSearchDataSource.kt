package com.qingyi.hear.core.search

import com.qingyi.hear.core.network.asArr
import com.qingyi.hear.core.network.asObj
import com.qingyi.hear.core.network.long
import com.qingyi.hear.core.network.str
import com.qingyi.hear.domain.MusicSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 网易云音乐搜索数据源。
 *
 * 使用网易云公开的非登录 web 搜索接口（`/api/search/get`），
 * 不携带 MUSIC_U 或任何 cookie，仅做匿名搜索请求。
 */
class NeteaseSearchDataSource(
    private val client: HttpClient,
    private val json: Json,
) : MusicSearchDataSource {
    companion object {
        private const val SEARCH_URL = "https://music.163.com/api/search/get"
        private const val REFERER = "https://music.163.com"
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
            val response: HttpResponse = client.submitForm(
                url = SEARCH_URL,
                formParameters = parameters {
                    append("s", keyword)
                    append("type", "1")
                    append("offset", "0")
                    append("total", "true")
                    append("limit", limit.toString())
                },
            ) {
                header("Referer", REFERER)
                header("User-Agent", USER_AGENT)
            }
            val body = json.parseToJsonElement(response.body<String>()).asObj() ?: return emptyList()
            val songs = body["result"]?.asObj()?.get("songs")?.asArr() ?: return emptyList()
            songs.mapNotNull { it.asObj()?.toResult() }
        }.getOrDefault(emptyList())
    }

    /** 兼容 `/api`（artists/album）与 `/weapi`（ar/al）两种字段命名。 */
    private fun JsonObject.toResult(): MusicSearchResult? {
        val id = this["id"]?.str() ?: return null
        val name = this["name"]?.str() ?: return null
        val artists = this["artists"]?.asArr() ?: this["ar"]?.asArr()
        val artist = artists
            ?.joinToString("/") { it.asObj()?.get("name").str().orEmpty() }
            .orEmpty()
        val album = (this["album"]?.asObj() ?: this["al"]?.asObj())?.get("name").str()
        // 网易云接口返回的 duration/dt 单位为毫秒
        val duration = this["duration"]?.long() ?: this["dt"]?.long()
        return MusicSearchResult(
            title = name,
            artist = artist,
            album = album,
            duration = duration,
            source = MusicSource.NETEASE_CLOUD,
            songId = id,
        )
    }
}
