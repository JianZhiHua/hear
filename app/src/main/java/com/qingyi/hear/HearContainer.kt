package com.qingyi.hear

import android.content.Context
import com.qingyi.hear.core.lyrics.LyricsRepository
import com.qingyi.hear.core.lyrics.LyricsRepositoryImpl
import com.qingyi.hear.core.lyrics.LyricsSyncEngine
import com.qingyi.hear.core.lyrics.NeteaseLyricsFetcher
import com.qingyi.hear.core.lyrics.QQLyricsFetcher
import com.qingyi.hear.core.network.NetworkModule
import com.qingyi.hear.core.search.MusicSearchRepository
import com.qingyi.hear.core.search.MusicSearchRepositoryImpl
import com.qingyi.hear.core.search.NeteaseSearchDataSource
import com.qingyi.hear.core.search.QQMusicSearchDataSource
import com.qingyi.hear.data.HistoryStore
import com.qingyi.hear.data.MediaSessionDataSource
import com.qingyi.hear.data.NotificationMusicDataSource
import com.qingyi.hear.data.MusicAggregationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HearContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val historyStore = HistoryStore(appContext)
    val mediaSessionSource = MediaSessionDataSource(appContext)
    val notificationMusicSource = NotificationMusicDataSource(appContext)

    val aggregationEngine = MusicAggregationEngine(
        mediaSessionSource = mediaSessionSource,
        notificationSource = notificationMusicSource,
        historyStore = historyStore,
        scope = appScope,
    )

    // ---------- 网络与无登录搜索 / 歌词 ----------
    private val httpClient = NetworkModule.httpClient
    private val json = NetworkModule.json

    private val neteaseSearch = NeteaseSearchDataSource(httpClient, json)
    private val qqSearch = QQMusicSearchDataSource(httpClient, json)
    val musicSearchRepository: MusicSearchRepository =
        MusicSearchRepositoryImpl(neteaseSearch, qqSearch)

    private val neteaseLyrics = NeteaseLyricsFetcher(httpClient, json)
    private val qqLyrics = QQLyricsFetcher(httpClient, json)
    val lyricsRepository: LyricsRepository =
        LyricsRepositoryImpl(neteaseLyrics, qqLyrics)

    val lyricsSyncEngine = LyricsSyncEngine()
}
