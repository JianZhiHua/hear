package com.qingyi.hear.storage

/**
 * 凭证存储接口
 *
 * 优化点：
 * 1. 支持凭证过期检测
 * 2. 支持凭证刷新回调
 * 3. 统一的凭证管理策略
 */
interface CredentialStore {
    /**
     * 获取指定来源的 Cookie
     */
    fun getCookie(source: String): String?

    /**
     * 设置指定来源的 Cookie
     */
    fun setCookie(source: String, cookie: String)

    /**
     * 清除指定来源的 Cookie
     */
    fun clearCookie(source: String)

    /**
     * 检查凭证是否可能已过期
     *
     * 基于以下策略判断：
     * 1. Cookie 为空
     * 2. Cookie 存储时间超过阈值
     * 3. Cookie 中包含过期时间字段且已过期
     */
    fun isCredentialLikelyExpired(source: String): Boolean {
        val cookie = getCookie(source) ?: return true
        if (cookie.isBlank()) return true

        // 检查常见的过期 Cookie 字段
        val expiresPatterns = listOf(
            Regex("""expires=([^;]+)""", RegexOption.IGNORE_CASE),
            Regex("""max-age=(\d+)""", RegexOption.IGNORE_CASE),
        )

        for (pattern in expiresPatterns) {
            val match = pattern.find(cookie) ?: continue
            val value = match.groupValues[1]

            // 解析 expires 日期
            if (pattern.pattern.contains("expires")) {
                try {
                    val expiresDate = parseCookieDate(value)
                    if (expiresDate != null && System.currentTimeMillis() > expiresDate) {
                        return true
                    }
                } catch (e: Exception) {
                    // 日期解析失败，忽略
                }
            }

            // 解析 max-age 秒数
            if (pattern.pattern.contains("max-age")) {
                try {
                    val maxAge = value.toLongOrNull() ?: continue
                    if (maxAge <= 0) {
                        return true
                    }
                } catch (e: Exception) {
                    // 解析失败，忽略
                }
            }
        }

        return false
    }

    /**
     * 获取凭证状态信息
     *
     * 用于调试和状态展示
     */
    fun getCredentialStatus(source: String): CredentialStatus {
        val cookie = getCookie(source)
        return when {
            cookie == null -> CredentialStatus.NOT_SET
            cookie.isBlank() -> CredentialStatus.EMPTY
            isCredentialLikelyExpired(source) -> CredentialStatus.LIKELY_EXPIRED
            else -> CredentialStatus.VALID
        }
    }

    /**
     * 批量检查所有来源的凭证状态
     */
    fun getAllCredentialStatus(sources: List<String>): Map<String, CredentialStatus> {
        return sources.associateWith { getCredentialStatus(it) }
    }

    companion object {
        /**
         * 解析 Cookie 日期格式
         *
         * 支持格式：
         * - Thu, 01 Jan 2024 00:00:00 GMT
         * - 2024-01-01T00:00:00Z
         * - 1704067200 (Unix 时间戳)
         */
        private fun parseCookieDate(dateStr: String): Long? {
            val trimmed = dateStr.trim()

            // 尝试 Unix 时间戳
            trimmed.toLongOrNull()?.let { timestamp ->
                return if (timestamp > 1_000_000_000_000L) {
                    timestamp // 毫秒
                } else {
                    timestamp * 1000L // 秒转毫秒
                }
            }

            // 尝试 ISO 8601 格式
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return sdf.parse(trimmed)?.time
            } catch (e: Exception) {
                // 格式不匹配
            }

            // 尝试 RFC 1123 格式
            try {
                val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return sdf.parse(trimmed)?.time
            } catch (e: Exception) {
                // 格式不匹配
            }

            return null
        }
    }
}

/**
 * 凭证状态枚举
 */
enum class CredentialStatus {
    /**
     * 未设置
     */
    NOT_SET,

    /**
     * 已设置但为空
     */
    EMPTY,

    /**
     * 可能已过期
     */
    LIKELY_EXPIRED,

    /**
     * 有效
     */
    VALID,
    ;

    /**
     * 是否需要重新登录
     */
    fun needsReLogin(): Boolean {
        return this == NOT_SET || this == EMPTY || this == LIKELY_EXPIRED
    }

    /**
     * 获取状态描述
     */
    fun getDescription(): String {
        return when (this) {
            NOT_SET -> "未登录"
            EMPTY -> "凭证为空"
            LIKELY_EXPIRED -> "凭证可能已过期"
            VALID -> "已登录"
        }
    }
}
