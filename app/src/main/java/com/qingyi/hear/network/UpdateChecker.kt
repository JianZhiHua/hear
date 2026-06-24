package com.qingyi.hear.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GitHub Release 更新检查器
 *
 * 通过 GitHub API 检查是否有新版本可用。
 * 支持语义版本号（v1.0.0）和日期版本号（v20260624-abc1234）格式。
 */
object UpdateChecker {

    private const val REPO = "JianZhiHua/hear"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /**
     * 检查是否有新版本
     *
     * @param currentVersion 当前应用版本号（如 "1.0.0"）
     * @param client OkHttpClient 实例
     * @return UpdateResult 更新检查结果
     */
    suspend fun checkForUpdate(
        currentVersion: String,
        client: OkHttpClient,
    ): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateResult.Error("HTTP ${response.code}")
            }

            val body = response.body?.string() ?: ""
            val json = Json.parseToJsonElement(body) as JsonObject

            val tagName = json["tag_name"]?.jsonPrimitive?.content
                ?: return@withContext UpdateResult.Error("无法解析版本号")
            val releaseUrl = json["html_url"]?.jsonPrimitive?.content
                ?: "https://github.com/$REPO/releases/latest"
            val releaseBody = json["body"]?.jsonPrimitive?.content ?: ""

            val latestVersion = tagName.removePrefix("v")
            val hasUpdate = compareVersions(latestVersion, currentVersion) > 0

            if (hasUpdate) {
                UpdateResult.Available(
                    version = latestVersion,
                    url = releaseUrl,
                    body = releaseBody,
                )
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "网络请求失败")
        }
    }

    /**
     * 比较两个版本号
     *
     * @return 正数表示 v1 > v2，负数表示 v1 < v2，0 表示相等
     */
    internal fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}

/**
 * 更新检查结果
 */
sealed class UpdateResult {
    /** 有新版本可用 */
    data class Available(
        val version: String,
        val url: String,
        val body: String,
    ) : UpdateResult()

    /** 已是最新版本 */
    data object UpToDate : UpdateResult()

    /** 检查失败 */
    data class Error(val message: String) : UpdateResult()
}
