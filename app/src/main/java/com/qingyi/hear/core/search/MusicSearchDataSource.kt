package com.qingyi.hear.core.search

/**
 * Platform-specific anonymous metadata search source.
 *
 * Implementations must not attach cookies, login credentials, DRM tokens, or
 * private playback URL logic. They only return searchable song metadata.
 */
interface MusicSearchDataSource {
    suspend fun search(keyword: String, limit: Int = 20): List<MusicSearchResult>
}
