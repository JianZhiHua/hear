package com.qingyi.hear.providers

import com.qingyi.hear.domain.AudioQuality
import com.qingyi.hear.domain.Lyrics
import com.qingyi.hear.domain.Playlist
import com.qingyi.hear.domain.StreamUrl
import com.qingyi.hear.domain.Track

interface MusicProvider {
    val source: String
    val displayName: String

    suspend fun search(keyword: String, limit: Int = 20, offset: Int = 0): List<Track>

    suspend fun fetchUserPlaylists(): List<Playlist>

    suspend fun fetchPlaylist(idOrUrl: String): Playlist

    suspend fun resolveStream(track: Track, quality: AudioQuality = AudioQuality.ExHigh): StreamUrl

    suspend fun fetchLyrics(track: Track): Lyrics
}

class ProviderError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
