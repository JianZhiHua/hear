package com.qingyi.hear.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qingyi.hear.domain.LyricSettings
import com.qingyi.hear.domain.PlayMode
import com.qingyi.hear.domain.Track
import com.qingyi.hear.network.HearJson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer

private val Context.hearDataStore by preferencesDataStore("hear.library")

class PlaybackQueueStore(private val context: Context) {
    private val queueKey = stringPreferencesKey("playback_queue")
    private val currentIndexKey = intPreferencesKey("playback_current_index")
    private val playModeKey = stringPreferencesKey("playback_play_mode")
    private val volumeKey = floatPreferencesKey("playback_volume")
    private val lyricSettingsKey = stringPreferencesKey("lyric_settings")
    private val trackListSerializer = ListSerializer(Track.serializer())

    suspend fun loadQueue(): List<Track> {
        val payload = context.hearDataStore.data.first()[queueKey] ?: return emptyList()
        return runCatching { HearJson.decodeFromString(trackListSerializer, payload) }.getOrDefault(emptyList())
    }

    suspend fun loadSnapshot(): StoredPlaybackSnapshot {
        val preferences = context.hearDataStore.data.first()
        val queue = preferences[queueKey]
            ?.let { payload -> runCatching { HearJson.decodeFromString(trackListSerializer, payload) }.getOrDefault(emptyList()) }
            ?: emptyList()
        val currentIndex = preferences[currentIndexKey]
            ?.takeIf { it in queue.indices }
            ?: -1
        val playMode = preferences[playModeKey]
            ?.let { value -> runCatching { PlayMode.valueOf(value) }.getOrNull() }
            ?: PlayMode.Order
        val volume = preferences[volumeKey]?.coerceIn(0f, 1f) ?: 1f
        val lyricSettings = preferences[lyricSettingsKey]
            ?.let { payload -> runCatching { HearJson.decodeFromString(LyricSettings.serializer(), payload) }.getOrNull() }
            ?: LyricSettings()
        return StoredPlaybackSnapshot(
            queue = queue,
            currentIndex = currentIndex,
            playMode = playMode,
            volume = volume,
            lyricSettings = lyricSettings,
        )
    }

    suspend fun saveQueue(tracks: List<Track>) {
        saveQueueState(tracks, currentIndex = -1)
    }

    suspend fun saveQueueState(tracks: List<Track>, currentIndex: Int) {
        val safeIndex = currentIndex.takeIf { it in tracks.indices } ?: -1
        val payload = HearJson.encodeToString(trackListSerializer, tracks)
        context.hearDataStore.edit { preferences ->
            preferences[queueKey] = payload
            preferences[currentIndexKey] = safeIndex
        }
    }

    suspend fun savePlayMode(playMode: PlayMode) {
        context.hearDataStore.edit { preferences ->
            preferences[playModeKey] = playMode.name
        }
    }

    suspend fun saveVolume(volume: Float) {
        context.hearDataStore.edit { preferences ->
            preferences[volumeKey] = volume.coerceIn(0f, 1f)
        }
    }

    suspend fun saveLyricSettings(settings: LyricSettings) {
        val payload = HearJson.encodeToString(LyricSettings.serializer(), settings)
        context.hearDataStore.edit { preferences ->
            preferences[lyricSettingsKey] = payload
        }
    }

}

data class StoredPlaybackSnapshot(
    val queue: List<Track>,
    val currentIndex: Int,
    val playMode: PlayMode,
    val volume: Float,
    val lyricSettings: LyricSettings,
)
