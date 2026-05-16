package com.qingyi.hear.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qingyi.hear.domain.Playlist
import com.qingyi.hear.domain.Track
import com.qingyi.hear.domain.trackQueueKey
import com.qingyi.hear.network.HearJson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

private val Context.libraryDataStore by preferencesDataStore("hear.local_library")

class LibraryStore(private val context: Context) {
    private val cachedPlaylistsKey = stringPreferencesKey("cached_playlists")
    private val localPlaylistsKey = stringPreferencesKey("local_playlists")
    private val updatedAtKey = longPreferencesKey("updated_at")
    private val playlistListSerializer = ListSerializer(Playlist.serializer())
    private val localPlaylistListSerializer = ListSerializer(LocalPlaylist.serializer())

    suspend fun loadSnapshot(): LibrarySnapshot {
        val preferences = context.libraryDataStore.data.first()
        val cachedPlaylists = preferences[cachedPlaylistsKey]
            ?.let { payload -> runCatching { HearJson.decodeFromString(playlistListSerializer, payload) }.getOrDefault(emptyList()) }
            ?: emptyList()
        val localPlaylists = preferences[localPlaylistsKey]
            ?.let { payload -> runCatching { HearJson.decodeFromString(localPlaylistListSerializer, payload) }.getOrDefault(emptyList()) }
            ?: emptyList()
        return LibrarySnapshot(
            cachedPlaylists = cachedPlaylists,
            localPlaylists = localPlaylists,
            updatedAtMs = preferences[updatedAtKey] ?: 0L,
        )
    }

    suspend fun saveCachedPlaylists(playlists: List<Playlist>) {
        val payload = HearJson.encodeToString(playlistListSerializer, normalizeRemotePlaylists(playlists))
        context.libraryDataStore.edit { preferences ->
            preferences[cachedPlaylistsKey] = payload
            preferences[updatedAtKey] = System.currentTimeMillis()
        }
    }

    suspend fun clearCachedPlaylists() {
        context.libraryDataStore.edit { preferences ->
            preferences.remove(cachedPlaylistsKey)
            preferences[updatedAtKey] = System.currentTimeMillis()
        }
    }

    suspend fun upsertCachedPlaylist(playlist: Playlist) {
        val snapshot = loadSnapshot()
        val next = snapshot.cachedPlaylists
            .filterNot { it.kind == playlist.kind && it.id == playlist.id } + normalizeRemotePlaylist(playlist)
        saveCachedPlaylists(next)
    }

    suspend fun createLocalPlaylist(name: String): Playlist {
        val now = System.currentTimeMillis()
        val playlist = LocalPlaylist(
            id = "local_$now",
            name = name.ifBlank { "新建歌单" },
            createdAtMs = now,
            updatedAtMs = now,
            tracks = emptyList(),
        )
        val snapshot = loadSnapshot()
        saveLocalPlaylists(snapshot.localPlaylists + playlist)
        return playlist.toPlaylist()
    }

    suspend fun deleteLocalPlaylist(id: String) {
        val snapshot = loadSnapshot()
        saveLocalPlaylists(snapshot.localPlaylists.filterNot { it.id == id })
    }

    suspend fun addTrackToLocalPlaylist(id: String, track: Track): Playlist? {
        val snapshot = loadSnapshot()
        val next = snapshot.localPlaylists.map { playlist ->
            if (playlist.id != id) {
                playlist
            } else {
                val exists = playlist.tracks.any { trackQueueKey(it) == trackQueueKey(track) }
                if (exists) {
                    playlist.copy(updatedAtMs = System.currentTimeMillis())
                } else {
                    playlist.copy(
                        updatedAtMs = System.currentTimeMillis(),
                        tracks = playlist.tracks + track,
                    )
                }
            }
        }
        saveLocalPlaylists(next)
        return next.firstOrNull { it.id == id }?.toPlaylist()
    }

    suspend fun removeTrackFromLocalPlaylist(id: String, index: Int): Playlist? {
        val snapshot = loadSnapshot()
        val next = snapshot.localPlaylists.map { playlist ->
            if (playlist.id == id && index in playlist.tracks.indices) {
                playlist.copy(
                    updatedAtMs = System.currentTimeMillis(),
                    tracks = playlist.tracks.filterIndexed { trackIndex, _ -> trackIndex != index },
                )
            } else {
                playlist
            }
        }
        saveLocalPlaylists(next)
        return next.firstOrNull { it.id == id }?.toPlaylist()
    }

    private suspend fun saveLocalPlaylists(playlists: List<LocalPlaylist>) {
        val payload = HearJson.encodeToString(localPlaylistListSerializer, playlists)
        context.libraryDataStore.edit { preferences ->
            preferences[localPlaylistsKey] = payload
            preferences[updatedAtKey] = System.currentTimeMillis()
        }
    }

    private fun normalizeRemotePlaylists(playlists: List<Playlist>): List<Playlist> =
        playlists.filterNot { it.kind == LOCAL_KIND }.map(::normalizeRemotePlaylist)

    private fun normalizeRemotePlaylist(playlist: Playlist): Playlist =
        playlist.copy(trackCount = playlist.trackCount ?: playlist.tracks.size)

    companion object {
        const val LOCAL_KIND = "local"
    }
}

data class LibrarySnapshot(
    val cachedPlaylists: List<Playlist>,
    val localPlaylists: List<LocalPlaylist>,
    val updatedAtMs: Long,
) {
    val allPlaylists: List<Playlist>
        get() = localPlaylists.map(LocalPlaylist::toPlaylist) + cachedPlaylists
}

@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val tracks: List<Track> = emptyList(),
) {
    fun toPlaylist(): Playlist =
        Playlist(
            id = id,
            name = name,
            kind = LibraryStore.LOCAL_KIND,
            tracks = tracks,
            trackCount = tracks.size,
            description = "本地歌单",
        )
}
