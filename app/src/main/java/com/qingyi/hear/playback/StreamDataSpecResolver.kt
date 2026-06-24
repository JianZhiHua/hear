@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.qingyi.hear.playback

import android.util.LruCache
import androidx.media3.datasource.DataSpec
import com.qingyi.hear.domain.StreamUrl
import com.qingyi.hear.domain.Track
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 流 URL 缓存条目
 */
private data class CachedStreamUrl(
    val streamUrl: StreamUrl,
    val cachedAtMs: Long = System.currentTimeMillis(),
) {
    /**
     * 检查 URL 是否已过期
     *
     * @param refreshAheadMs 提前多少毫秒认为过期（默认30秒）
     */
    fun isExpired(refreshAheadMs: Long = 30_000L): Boolean {
        val expiresAtMs = streamUrl.expiresAtEpochSeconds?.let { it * 1000L } ?: return false
        return System.currentTimeMillis() >= (expiresAtMs - refreshAheadMs)
    }
}

/**
 * 流 URL 缓存管理器
 *
 * 优化点：
 * 1. 缓存已解析的流 URL，避免重复请求
 * 2. 自动检测 URL 过期，提前刷新
 * 3. 线程安全的并发访问
 * 4. LRU 策略限制缓存大小
 */
object StreamUrlCache {
    private const val MAX_CACHE_SIZE = 100
    private const val DEFAULT_REFRESH_AHEAD_MS = 30_000L // 30秒

    private val cache = LruCache<String, CachedStreamUrl>(MAX_CACHE_SIZE)
    private val refreshLocks = ConcurrentHashMap<String, Any>()

    /**
     * 获取缓存的流 URL
     *
     * @return 缓存的 URL，如果不存在或已过期返回 null
     */
    fun get(trackId: String, refreshAheadMs: Long = DEFAULT_REFRESH_AHEAD_MS): StreamUrl? {
        val cached = cache.get(trackId) ?: return null
        return if (cached.isExpired(refreshAheadMs)) {
            cache.remove(trackId)
            null
        } else {
            cached.streamUrl
        }
    }

    /**
     * 存储流 URL 到缓存
     */
    fun put(trackId: String, streamUrl: StreamUrl) {
        cache.put(trackId, CachedStreamUrl(streamUrl))
    }

    /**
     * 移除缓存
     */
    fun remove(trackId: String) {
        cache.remove(trackId)
    }

    /**
     * 清空缓存
     */
    fun clear() {
        cache.evictAll()
    }

    /**
     * 获取刷新锁，防止并发刷新
     */
    fun getRefreshLock(trackId: String): Any {
        return refreshLocks.computeIfAbsent(trackId) { Any() }
    }

    /**
     * 释放刷新锁
     */
    fun releaseRefreshLock(trackId: String) {
        refreshLocks.remove(trackId)
    }
}

fun resolveTrackDataSpec(
    dataSpec: DataSpec,
    findTrack: (String) -> Track?,
    resolveStream: (Track) -> StreamUrl,
): DataSpec {
    val mediaId = mediaIdFromStreamUri(dataSpec.uri) ?: return dataSpec
    val resolved = resolveTrackStreamRequest(
        mediaId = mediaId,
        baseHeaders = dataSpec.httpRequestHeaders,
        findTrack = findTrack,
        resolveStream = resolveStream,
    )
    return dataSpec.buildUpon()
        .setUri(resolved.url)
        .setHttpRequestHeaders(resolved.headers)
        .setKey(resolved.key)
        .build()
}

/**
 * 解析音轨流请求
 *
 * 优化点：
 * 1. 集成 URL 缓存，减少重复请求
 * 2. 过期检测和自动刷新
 * 3. 并发安全的刷新机制
 */
fun resolveTrackStreamRequest(
    mediaId: String,
    baseHeaders: Map<String, String> = emptyMap(),
    findTrack: (String) -> Track?,
    resolveStream: (Track) -> StreamUrl,
): ResolvedTrackStream {
    // 尝试从缓存获取
    val cachedStream = StreamUrlCache.get(mediaId)
    if (cachedStream != null) {
        return ResolvedTrackStream(
            url = cachedStream.url,
            headers = baseHeaders + cachedStream.headers,
            key = mediaId,
        )
    }

    val track = findTrack(mediaId)
        ?: throw IOException("播放队列中找不到这首歌")

    // 使用锁防止并发刷新
    val lock = StreamUrlCache.getRefreshLock(mediaId)
    synchronized(lock) {
        try {
            // 双重检查：可能其他线程已经刷新了
            val refreshedCache = StreamUrlCache.get(mediaId)
            if (refreshedCache != null) {
                return ResolvedTrackStream(
                    url = refreshedCache.url,
                    headers = baseHeaders + refreshedCache.headers,
                    key = mediaId,
                )
            }

            // 解析新的流 URL
            val stream = resolveStream(track)
            if (stream.url.isBlank()) {
                throw IOException("没有获取到可播放链接")
            }

            // 缓存新的 URL
            StreamUrlCache.put(mediaId, stream)

            return ResolvedTrackStream(
                url = stream.url,
                headers = baseHeaders + stream.headers,
                key = mediaId,
            )
        } finally {
            StreamUrlCache.releaseRefreshLock(mediaId)
        }
    }
}

/**
 * 强制刷新指定音轨的流 URL
 *
 * 用于 URL 失效时手动触发刷新
 */
fun forceRefreshTrackStream(
    mediaId: String,
    baseHeaders: Map<String, String> = emptyMap(),
    findTrack: (String) -> Track?,
    resolveStream: (Track) -> StreamUrl,
): ResolvedTrackStream {
    // 移除缓存
    StreamUrlCache.remove(mediaId)

    // 重新解析
    return resolveTrackStreamRequest(
        mediaId = mediaId,
        baseHeaders = baseHeaders,
        findTrack = findTrack,
        resolveStream = resolveStream,
    )
}

data class ResolvedTrackStream(
    val url: String,
    val headers: Map<String, String>,
    val key: String,
)
