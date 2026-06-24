package com.qingyi.hear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingyi.hear.HearApplication
import com.qingyi.hear.domain.LyricLine
import com.qingyi.hear.domain.LyricSettings
import com.qingyi.hear.domain.Lyrics
import com.qingyi.hear.domain.PlayMode
import com.qingyi.hear.domain.AudioQuality
import com.qingyi.hear.domain.Playlist
import com.qingyi.hear.domain.Track
import com.qingyi.hear.domain.activeLyricIndex
import com.qingyi.hear.domain.parseLyrics
import com.qingyi.hear.domain.trackQueueKey
import com.qingyi.hear.providers.MusicProvider
import com.qingyi.hear.network.UpdateChecker
import com.qingyi.hear.network.UpdateResult
import com.qingyi.hear.storage.LibraryStore
import com.qingyi.hear.storage.ShizukuCookieExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HearViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as HearApplication).container
    private val credentialStore = container.credentialStore
    private val queueStore = container.queueStore
    private val libraryStore = container.libraryStore
    private val providers = container.providers
    private val providerBySource = container.providerBySource
    private val playbackManager = container.playbackManager
    private var currentLyricTrackKey: String? = null
    private var lastPlaybackErrorMessage: String? = null

    private val _state = MutableStateFlow(
        HearUiState(
            providers = providers.map { ProviderStatus(it.source, it.displayName, hasCookie = hasCookie(it.source)) },
        ),
    )
    val state: StateFlow<HearUiState> = _state.asStateFlow()

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()

    init {
        viewModelScope.launch {
            val snapshot = queueStore.loadSnapshot()
            _state.value = _state.value.copy(
                lyricSettings = snapshot.lyricSettings,
                audioQuality = snapshot.audioQuality,
            )
        }
        viewModelScope.launch {
            val snapshot = libraryStore.loadSnapshot()
            _state.value = _state.value.copy(
                playlists = snapshot.allPlaylists,
                cachedLibraryUpdatedAtMs = snapshot.updatedAtMs,
            )
        }
        viewModelScope.launch {
            playbackManager.queueState.collectLatest { queueState ->
                _state.value = _state.value.copy(
                    queue = queueState.queue,
                    currentIndex = queueState.currentIndex,
                    playMode = queueState.playMode,
                    volume = queueState.volume,
                    audioQuality = queueState.audioQuality,
                )
            }
        }
        viewModelScope.launch {
            playbackManager.sleepTimerRemainingMs.collectLatest { remaining ->
                _state.value = _state.value.copy(sleepTimerRemainingMs = remaining)
            }
        }
        viewModelScope.launch {
            playbackManager.state.collectLatest { playbackState ->
                val activeIndex = activeLyricIndex(_state.value.lyricLines, playbackState.positionMs)
                val playbackMessage = playbackState.errorMessage
                    ?.let { "播放失败：$it" }
                    ?.takeIf { it != lastPlaybackErrorMessage }
                if (playbackState.errorMessage == null) {
                    lastPlaybackErrorMessage = null
                } else if (playbackMessage != null) {
                    lastPlaybackErrorMessage = playbackMessage
                }
                _state.value = _state.value.copy(
                    currentTrack = playbackState.currentTrack,
                    currentIndex = playbackState.currentIndex,
                    isPlaying = playbackState.isPlaying,
                    isBuffering = playbackState.isBuffering,
                    positionMs = playbackState.positionMs,
                    durationMs = playbackState.durationMs,
                    volume = playbackState.volume,
                    activeLyricIndex = activeIndex,
                    message = playbackMessage ?: _state.value.message,
                )
                maybeFetchLyrics(playbackState.currentTrack)
            }
        }
        // Shizuku 状态监听
        try {
            rikka.shizuku.Shizuku.addBinderReceivedListener(shizukuBinderReceivedListener)
            rikka.shizuku.Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
            rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
            refreshShizukuState()
        } catch (_: Exception) {
            // Shizuku 未安装，忽略
        }
    }

    // Shizuku 状态监听器
    private val shizukuBinderReceivedListener = rikka.shizuku.Shizuku.OnBinderReceivedListener {
        refreshShizukuState()
    }
    private val shizukuBinderDeadListener = rikka.shizuku.Shizuku.OnBinderDeadListener {
        _isShizukuAvailable.value = false
    }
    private val shizukuPermissionResultListener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, _ ->
        refreshShizukuState()
    }

    private fun refreshShizukuState() {
        _isShizukuAvailable.value = ShizukuCookieExtractor.isAvailable()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            rikka.shizuku.Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
            rikka.shizuku.Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
            rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        } catch (_: Exception) {
            // Shizuku 未安装，忽略
        }
    }

    fun updateKeyword(value: String) {
        _state.value = _state.value.copy(keyword = value)
    }

    fun updatePlaylistInput(value: String) {
        _state.value = _state.value.copy(playlistInput = value)
    }

    fun updateLocalPlaylistName(value: String) {
        _state.value = _state.value.copy(localPlaylistName = value)
    }

    fun consumeMessage(message: String) {
        if (_state.value.message == message) {
            _state.value = _state.value.copy(message = null)
        }
    }

    fun saveCookie(source: String, cookie: String) {
        credentialStore.setCookie(source, cookie)
        refreshProviderStatuses("已保存 ${displayName(source)} Cookie")
    }

    fun clearCookie(source: String) {
        credentialStore.clearCookie(source)
        refreshProviderStatuses("已清除 ${displayName(source)} Cookie")
    }

    /**
     * 通过 Shizuku 从已安装的 APP 自动提取 Cookie
     */
    fun extractCookieFromApp(source: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, message = "正在通过 Shizuku 提取 ${displayName(source)} Cookie...")
            try {
                // 检查 Shizuku 状态
                if (!ShizukuCookieExtractor.isInstalled()) {
                    _state.value = _state.value.copy(
                        isBusy = false,
                        message = "请先安装并激活 Shizuku",
                    )
                    return@launch
                }
                if (!ShizukuCookieExtractor.isAvailable()) {
                    val granted = ShizukuCookieExtractor.requestPermission()
                    if (!granted) {
                        _state.value = _state.value.copy(
                            isBusy = false,
                            message = "Shizuku 权限被拒绝",
                        )
                        return@launch
                    }
                }
                // 提取
                val result = withContext(Dispatchers.IO) {
                    when (source) {
                        "qq" -> ShizukuCookieExtractor.extractQQCookie()
                        "netease" -> ShizukuCookieExtractor.extractNetEaseCookie()
                        else -> Result.failure(IllegalStateException("不支持的平台"))
                    }
                }
                result.fold(
                    onSuccess = { cookie ->
                        credentialStore.setCookie(source, cookie)
                        refreshProviderStatuses("已自动提取 ${displayName(source)} Cookie")
                    },
                    onFailure = { error ->
                        _state.value = _state.value.copy(
                            isBusy = false,
                            message = "提取失败：${error.message}",
                        )
                    },
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isBusy = false,
                    message = "提取异常：${e.message}",
                )
            }
        }
    }


    fun search() {
        val keyword = _state.value.keyword.trim()
        if (keyword.isBlank()) {
            _state.value = _state.value.copy(message = "请先输入歌曲、歌手或专辑关键词")
            return
        }
        viewModelScope.launch {
            runBusy("正在搜索...") {
                val results = providers.map { provider ->
                    async(Dispatchers.IO) {
                        runCatching { provider.search(keyword, limit = 20, offset = 0) }
                            .fold(
                                onSuccess = { it },
                                onFailure = { error ->
                                    appendMessage("${provider.displayName}：${friendlyError(error)}")
                                    emptyList()
                                },
                            )
                    }
                }.awaitAll().flatten()
                val pageSize = 20
                _state.value = _state.value.copy(
                    searchResults = results,
                    searchOffset = results.size,
                    canLoadMoreSearch = results.size >= pageSize,
                    lastSearchSource = null,
                    selectedPlaylist = null,
                    message = if (results.isEmpty()) "没有找到歌曲" else "找到 ${results.size} 首歌曲",
                )
            }
        }
    }

    fun loadMoreSearchResults(selectedSource: String) {
        val keyword = _state.value.keyword.trim()
        if (keyword.isBlank() || _state.value.isLoadingMoreSearch || !_state.value.canLoadMoreSearch) return
        val provider = providerBySource[selectedSource] ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMoreSearch = true)
            try {
                val pageSize = 20
                val newResults = withContext(Dispatchers.IO) {
                    provider.search(keyword, limit = pageSize, offset = _state.value.searchOffset)
                }
                if (newResults.isEmpty()) {
                    _state.value = _state.value.copy(
                        isLoadingMoreSearch = false,
                        canLoadMoreSearch = false,
                        message = "没有更多结果了",
                    )
                } else {
                    _state.value = _state.value.copy(
                        searchResults = _state.value.searchResults + newResults,
                        searchOffset = _state.value.searchOffset + newResults.size,
                        isLoadingMoreSearch = false,
                        canLoadMoreSearch = newResults.size >= pageSize,
                    )
                }
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    isLoadingMoreSearch = false,
                    message = friendlyError(error),
                )
            }
        }
    }

    fun loadUserPlaylists(source: String) {
        val provider = providerBySource[source] ?: return
        viewModelScope.launch {
            runBusy("正在获取歌单...") {
                val playlists = withContext(Dispatchers.IO) { provider.fetchUserPlaylists() }
                val retainedRemote = remotePlaylists(_state.value.playlists).filterNot { it.kind == source }
                val merged = mergePlaylists(retainedRemote + playlists, localPlaylists())
                libraryStore.saveCachedPlaylists(remotePlaylists(merged))
                _state.value = _state.value.copy(
                    playlists = merged,
                    selectedPlaylist = null,
                    cachedLibraryUpdatedAtMs = System.currentTimeMillis(),
                    message = "已从 ${provider.displayName} 获取 ${playlists.size} 个歌单",
                )
            }
        }
    }

    fun loadAllUserPlaylists() {
        viewModelScope.launch {
            runBusy("正在同步所有歌单...") {
                val results = providers.map { provider ->
                    async(Dispatchers.IO) {
                        if (!hasCookie(provider.source)) {
                            PlaylistLoadResult(
                                playlists = emptyList(),
                                message = "${provider.displayName} 未配置 Cookie",
                            )
                        } else {
                            runCatching { provider.fetchUserPlaylists() }
                                .fold(
                                    onSuccess = { PlaylistLoadResult(playlists = it) },
                                    onFailure = { error ->
                                        PlaylistLoadResult(
                                            playlists = emptyList(),
                                            message = "${provider.displayName}：${friendlyError(error)}",
                                        )
                                    },
                                )
                        }
                    }
                }.awaitAll()
                val playlists = results.flatMap { it.playlists }
                val warnings = results.mapNotNull { it.message }.filter(String::isNotBlank)
                val merged = mergePlaylists(playlists, localPlaylists())
                libraryStore.saveCachedPlaylists(remotePlaylists(merged))
                _state.value = _state.value.copy(
                    playlists = merged,
                    selectedPlaylist = null,
                    cachedLibraryUpdatedAtMs = System.currentTimeMillis(),
                    message = buildList {
                        add(if (playlists.isEmpty()) "没有同步到歌单" else "已同步 ${playlists.size} 个歌单")
                        addAll(warnings)
                    }.joinToString("\n"),
                )
            }
        }
    }

    fun importPlaylist(source: String) {
        val provider = providerBySource[source] ?: return
        val input = _state.value.playlistInput.trim()
        if (input.isBlank()) {
            _state.value = _state.value.copy(message = "请先输入歌单 ID 或链接")
            return
        }
        viewModelScope.launch {
            runBusy("正在导入歌单...") {
                val playlist = withContext(Dispatchers.IO) { provider.fetchPlaylist(input) }
                libraryStore.upsertCachedPlaylist(playlist)
                val merged = upsertPlaylist(_state.value.playlists, playlist)
                _state.value = _state.value.copy(
                    selectedPlaylist = playlist,
                    searchResults = playlist.tracks,
                    playlists = merged,
                    cachedLibraryUpdatedAtMs = System.currentTimeMillis(),
                    message = "已导入：${playlist.name}",
                )
            }
        }
    }

    fun openPlaylist(playlist: Playlist) {
        if (playlist.kind == LibraryStore.LOCAL_KIND) {
            _state.value = _state.value.copy(
                selectedPlaylist = playlist,
                searchResults = playlist.tracks,
                message = null,
            )
            return
        }
        val provider = providerBySource[playlist.kind] ?: return
        viewModelScope.launch {
            runBusy("正在打开歌单...") {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching { provider.fetchPlaylist(playlist.id) }
                        .getOrElse { error ->
                            playlist.takeIf { it.tracks.isNotEmpty() } ?: throw error
                        }
                }
                libraryStore.upsertCachedPlaylist(loaded)
                _state.value = _state.value.copy(
                    selectedPlaylist = loaded,
                    searchResults = loaded.tracks,
                    playlists = upsertPlaylist(_state.value.playlists, loaded),
                    cachedLibraryUpdatedAtMs = System.currentTimeMillis(),
                    message = "已打开：${loaded.name}",
                )
            }
        }
    }

    fun closePlaylist() {
        _state.value = _state.value.copy(
            selectedPlaylist = null,
            searchResults = emptyList(),
            message = null,
        )
    }

    fun play(track: Track) {
        _state.value = _state.value.copy(message = "正在播放：${track.title}")
        playbackManager.play(track)
    }

    fun playPlaylist(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            _state.value = _state.value.copy(message = "当前歌单没有可播放歌曲")
            return
        }
        _state.value = _state.value.copy(message = "已替换播放列表，共 ${tracks.size} 首")
        playbackManager.playQueue(tracks)
    }

    fun addToPlaybackQueue(track: Track) {
        val added = playbackManager.addToQueue(track)
        _state.value = _state.value.copy(
            message = if (added) {
                "已加入当前播放列表：${track.title}"
            } else {
                "播放列表中已存在：${track.title}"
            },
        )
    }

    fun createLocalPlaylist() {
        val name = _state.value.localPlaylistName.trim()
        viewModelScope.launch {
            val playlist = libraryStore.createLocalPlaylist(name.ifBlank { "我的歌单" })
            val merged = upsertPlaylist(_state.value.playlists, playlist)
            _state.value = _state.value.copy(
                playlists = merged,
                selectedPlaylist = playlist,
                searchResults = emptyList(),
                localPlaylistName = "",
                cachedLibraryUpdatedAtMs = System.currentTimeMillis(),
                message = "已创建本地歌单：${playlist.name}",
            )
        }
    }

    fun deleteSelectedLocalPlaylist() {
        val playlist = _state.value.selectedPlaylist?.takeIf { it.kind == LibraryStore.LOCAL_KIND } ?: return
        viewModelScope.launch {
            libraryStore.deleteLocalPlaylist(playlist.id)
            _state.value = _state.value.copy(
                playlists = _state.value.playlists.filterNot { it.kind == LibraryStore.LOCAL_KIND && it.id == playlist.id },
                selectedPlaylist = null,
                searchResults = emptyList(),
                cachedLibraryUpdatedAtMs = System.currentTimeMillis(),
                message = "已删除本地歌单：${playlist.name}",
            )
        }
    }

    fun clearRemotePlaylistCache() {
        viewModelScope.launch {
            libraryStore.clearCachedPlaylists()
            _state.value = _state.value.copy(
                playlists = localPlaylists(),
                selectedPlaylist = _state.value.selectedPlaylist?.takeIf { it.kind == LibraryStore.LOCAL_KIND },
                searchResults = _state.value.selectedPlaylist?.takeIf { it.kind == LibraryStore.LOCAL_KIND }?.tracks ?: emptyList(),
                cachedLibraryUpdatedAtMs = System.currentTimeMillis(),
                message = "已清理平台歌单缓存，本地歌单已保留",
            )
        }
    }

    fun addTrackToFirstLocalPlaylist(track: Track) {
        viewModelScope.launch {
            val local = localPlaylists().firstOrNull()
                ?: libraryStore.createLocalPlaylist("我的收藏")
            val updated = libraryStore.addTrackToLocalPlaylist(local.id, track) ?: local
            applyUpdatedLocalPlaylist(updated)
        }
    }

    fun addTrackToLocalPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            val updated = libraryStore.addTrackToLocalPlaylist(playlistId, track)
                ?: _state.value.playlists.firstOrNull { it.kind == LibraryStore.LOCAL_KIND && it.id == playlistId }
                ?: return@launch
            applyUpdatedLocalPlaylist(updated)
        }
    }

    fun removeTrackFromSelectedLocalPlaylist(index: Int) {
        val playlist = _state.value.selectedPlaylist?.takeIf { it.kind == LibraryStore.LOCAL_KIND } ?: return
        viewModelScope.launch {
            val updated = libraryStore.removeTrackFromLocalPlaylist(playlist.id, index) ?: return@launch
            _state.value = _state.value.copy(
                playlists = upsertPlaylist(_state.value.playlists, updated),
                selectedPlaylist = updated,
                searchResults = updated.tracks,
                cachedLibraryUpdatedAtMs = System.currentTimeMillis(),
                message = "已从本地歌单移除歌曲",
            )
        }
    }

    fun playQueueItem(index: Int) {
        playbackManager.playQueueItem(index)
    }

    fun togglePlayback() {
        playbackManager.toggle()
    }

    fun previous() {
        playbackManager.previous()
    }

    fun next() {
        playbackManager.next()
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        playbackManager.setVolume(volume)
    }

    fun setPlayMode(playMode: PlayMode) {
        playbackManager.setPlayMode(playMode)
    }

    fun setAudioQuality(quality: AudioQuality) {
        playbackManager.setAudioQuality(quality)
        _state.value = _state.value.copy(message = "音质已切换：${quality.displayName}")
    }

    fun setSleepTimer(minutes: Int) {
        playbackManager.setSleepTimer(minutes)
        _state.value = _state.value.copy(
            message = if (minutes > 0) "定时停止：${minutes} 分钟" else "已取消定时停止",
        )
    }

    fun cancelSleepTimer() {
        playbackManager.cancelSleepTimer()
        _state.value = _state.value.copy(message = "已取消定时停止")
    }

    /**
     * 检查 GitHub 是否有新版本
     */
    fun checkForUpdate() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCheckingUpdate = true)
            try {
                val context = getApplication<Application>()
                val currentVersion = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
                val client = container.client
                val result = UpdateChecker.checkForUpdate(currentVersion, client)
                _state.value = _state.value.copy(
                    isCheckingUpdate = false,
                    updateResult = result,
                    message = when (result) {
                        is UpdateResult.Available -> null // 通过对话框显示
                        is UpdateResult.UpToDate -> "已是最新版本"
                        is UpdateResult.Error -> "检查更新失败：${result.message}"
                    },
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isCheckingUpdate = false,
                    message = "检查更新失败：${e.message}",
                )
            }
        }
    }

    /**
     * 清除更新检查结果
     */
    fun clearUpdateResult() {
        _state.value = _state.value.copy(updateResult = null)
    }

    fun removeQueueItem(index: Int) {
        playbackManager.removeQueueItem(index)
        _state.value = _state.value.copy(message = "已从队列移除歌曲")
    }

    fun clearQueue() {
        currentLyricTrackKey = null
        playbackManager.clearQueue()
        _state.value = _state.value.copy(
            currentTrack = null,
            lyrics = null,
            lyricLines = emptyList(),
            activeLyricIndex = null,
            lyricStatus = "暂无歌词",
            message = "播放队列已清空",
        )
    }

    fun updateLyricSetting(settings: LyricSettings) {
        val normalized = settings.copy(
            fontSizeSp = settings.fontSizeSp.coerceIn(12f, 48f),
            lineSpacing = settings.lineSpacing.coerceIn(1.0f, 2.0f),
        )
        _state.value = _state.value.copy(lyricSettings = normalized)
        viewModelScope.launch {
            queueStore.saveLyricSettings(normalized)
        }
    }

    private fun maybeFetchLyrics(track: Track?) {
        if (track == null) {
            if (currentLyricTrackKey != null) {
                currentLyricTrackKey = null
                _state.value = _state.value.copy(
                    lyrics = null,
                    lyricLines = emptyList(),
                    activeLyricIndex = null,
                    lyricStatus = "暂无歌词",
                )
            }
            return
        }
        val key = trackQueueKey(track)
        if (key == currentLyricTrackKey) return
        currentLyricTrackKey = key
        _state.value = _state.value.copy(
            lyrics = null,
            lyricLines = emptyList(),
            activeLyricIndex = null,
            lyricStatus = "正在获取歌词...",
        )
        val provider = providerBySource[track.source] ?: return
        viewModelScope.launch {
            fetchLyricsFor(provider, track)
        }
    }

    private suspend fun fetchLyricsFor(provider: MusicProvider, track: Track) {
        val result = withContext(Dispatchers.IO) {
            runCatching { provider.fetchLyrics(track) }
        }
        result.fold(
            onSuccess = { lyrics ->
                val text = lyrics.text.ifBlank { lyrics.translatedText.orEmpty() }
                val lines = parseLyrics(text)
                _state.value = _state.value.copy(
                    lyrics = lyrics,
                    lyricLines = lines,
                    activeLyricIndex = activeLyricIndex(lines, _state.value.positionMs),
                    lyricStatus = if (lines.isEmpty()) "暂无歌词" else null,
                )
            },
            onFailure = { error ->
                _state.value = _state.value.copy(
                    lyrics = Lyrics(""),
                    lyricLines = emptyList(),
                    activeLyricIndex = null,
                    lyricStatus = "歌词获取失败：${friendlyError(error)}",
                )
            },
        )
    }

    private suspend fun runBusy(label: String, block: suspend () -> Unit) {
        _state.value = _state.value.copy(isBusy = true, message = label)
        try {
            block()
        } catch (error: Throwable) {
            _state.value = _state.value.copy(message = friendlyError(error))
        } finally {
            _state.value = _state.value.copy(isBusy = false)
        }
    }

    private fun appendMessage(value: String) {
        if (value.isBlank()) return
        val current = _state.value.message.orEmpty()
        _state.value = _state.value.copy(message = listOf(current, value).filter(String::isNotBlank).joinToString("\n"))
    }

    private fun refreshProviderStatuses(message: String? = null) {
        _state.value = _state.value.copy(
            providers = providers.map { ProviderStatus(it.source, it.displayName, hasCookie = hasCookie(it.source)) },
            message = message,
        )
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message ?: error::class.java.simpleName
        return when {
            raw.contains("cookie", ignoreCase = true) && raw.contains("qq", ignoreCase = true) ->
                "QQ 音乐需要先填写 Cookie"

            raw.contains("cookie", ignoreCase = true) && raw.contains("netease", ignoreCase = true) ->
                "网易云音乐需要先填写 Cookie"

            raw.contains("playable URL", ignoreCase = true) || raw.contains("可播放链接") ->
                "没有获取到可播放链接，可能受会员、版权或账号权限限制"

            raw.contains("playlist", ignoreCase = true) && raw.contains("ID", ignoreCase = true) ->
                "请检查歌单 ID 或链接是否正确"

            raw.contains("HTTP", ignoreCase = true) ->
                "网络请求失败：$raw"

            raw.contains("resolver", ignoreCase = true) ->
                "歌曲缺少播放解析信息"

            else -> raw
        }
    }

    private fun hasCookie(source: String): Boolean = !credentialStore.getCookie(source).isNullOrBlank()

    private fun displayName(source: String): String = providerBySource[source]?.displayName ?: source

    private fun applyUpdatedLocalPlaylist(updated: Playlist) {
        _state.value = _state.value.copy(
            playlists = upsertPlaylist(_state.value.playlists, updated),
            selectedPlaylist = _state.value.selectedPlaylist?.let { current ->
                if (current.kind == LibraryStore.LOCAL_KIND && current.id == updated.id) updated else current
            },
            searchResults = if (_state.value.selectedPlaylist?.id == updated.id) updated.tracks else _state.value.searchResults,
            cachedLibraryUpdatedAtMs = System.currentTimeMillis(),
            message = "已加入本地歌单：${updated.name}",
        )
    }

    private fun localPlaylists(): List<Playlist> =
        _state.value.playlists.filter { it.kind == LibraryStore.LOCAL_KIND }

    private fun remotePlaylists(playlists: List<Playlist>): List<Playlist> =
        playlists.filterNot { it.kind == LibraryStore.LOCAL_KIND }

    private fun mergePlaylists(remote: List<Playlist>, local: List<Playlist>): List<Playlist> =
        local + remote

    private fun upsertPlaylist(playlists: List<Playlist>, playlist: Playlist): List<Playlist> =
        listOf(playlist) + playlists.filterNot { it.kind == playlist.kind && it.id == playlist.id }
}

data class HearUiState(
    val providers: List<ProviderStatus> = emptyList(),
    val keyword: String = "",
    val playlistInput: String = "",
    val searchResults: List<Track> = emptyList(),
    val searchOffset: Int = 0,
    val canLoadMoreSearch: Boolean = false,
    val isLoadingMoreSearch: Boolean = false,
    val lastSearchSource: String? = null,
    val playlists: List<Playlist> = emptyList(),
    val selectedPlaylist: Playlist? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrack: Track? = null,
    val lyrics: Lyrics? = null,
    val lyricLines: List<LyricLine> = emptyList(),
    val activeLyricIndex: Int? = null,
    val lyricStatus: String? = "暂无歌词",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val playMode: PlayMode = PlayMode.Order,
    val audioQuality: AudioQuality = AudioQuality.ExHigh,
    val lyricSettings: LyricSettings = LyricSettings(),
    val sleepTimerRemainingMs: Long? = null,
    val localPlaylistName: String = "",
    val cachedLibraryUpdatedAtMs: Long = 0L,
    val isBusy: Boolean = false,
    val message: String? = null,
    val isCheckingUpdate: Boolean = false,
    val updateResult: UpdateResult? = null,
)

data class ProviderStatus(
    val source: String,
    val displayName: String,
    val hasCookie: Boolean,
)

private data class PlaylistLoadResult(
    val playlists: List<Playlist>,
    val message: String? = null,
)
