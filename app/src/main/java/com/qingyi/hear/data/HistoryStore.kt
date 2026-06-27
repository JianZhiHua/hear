package com.qingyi.hear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qingyi.hear.domain.MusicInfo
import com.qingyi.hear.domain.MusicSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "play_history")
private val HISTORY_KEY = stringPreferencesKey("history_json")

@Serializable
data class HistoryEntry(
    val title: String,
    val artist: String,
    val album: String? = null,
    val appPackage: String,
    val appName: String,
    val source: String,
    val duration: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
)

class HistoryStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val history: Flow<List<HistoryEntry>> = context.historyDataStore.data.map { prefs ->
        val raw = prefs[HISTORY_KEY] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<HistoryEntry>>(raw) }
            .getOrElse { emptyList() }
    }

    suspend fun addEntry(music: MusicInfo) {
        context.historyDataStore.edit { prefs ->
            val current = runCatching {
                json.decodeFromString<List<HistoryEntry>>(prefs[HISTORY_KEY] ?: "[]")
            }.getOrElse { emptyList() }
            val entry = HistoryEntry(
                title = music.title,
                artist = music.artist,
                album = music.album,
                appPackage = music.appPackage,
                appName = music.appName,
                source = music.source.name,
                duration = music.duration,
            )
            val updated = (listOf(entry) + current).take(200)
            prefs[HISTORY_KEY] = json.encodeToString(updated)
        }
    }
}
