package com.qingyi.hear

import android.content.Context
import com.qingyi.hear.network.HearDns
import com.qingyi.hear.playback.HearPlaybackManager
import com.qingyi.hear.providers.MusicProvider
import com.qingyi.hear.providers.netease.NetEaseProvider
import com.qingyi.hear.providers.qq.QQProvider
import com.qingyi.hear.storage.EncryptedCookieStore
import com.qingyi.hear.storage.LibraryStore
import com.qingyi.hear.storage.PlaybackQueueStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class HearContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(HearDns())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(70, TimeUnit.SECONDS)
        .build()
    val credentialStore = EncryptedCookieStore(appContext)
    val queueStore = PlaybackQueueStore(appContext)
    val libraryStore = LibraryStore(appContext)
    val providers: List<MusicProvider> = listOf(
        QQProvider(client, credentialStore),
        NetEaseProvider(client, credentialStore),
    )
    val providerBySource: Map<String, MusicProvider> = providers.associateBy { it.source }
    val playbackManager = HearPlaybackManager(
        context = appContext,
        appScope = appScope,
        queueStore = queueStore,
        client = client,
        providerBySource = providerBySource,
    )
}
