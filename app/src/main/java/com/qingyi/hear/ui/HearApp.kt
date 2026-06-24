package com.qingyi.hear.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingyi.hear.storage.LibraryStore

private enum class AppTab {
    Library,
    Search,
    Settings,
}

@Composable
fun HearApp(viewModel: HearViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cookieInputs = remember { mutableStateMapOf<String, String>() }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showLyricSettings by rememberSaveable { mutableStateOf(false) }
    var sleepTimerMinutes by rememberSaveable { mutableIntStateOf(0) }
    var trackForLocalPlaylist by remember { mutableStateOf<com.qingyi.hear.domain.Track?>(null) }
    val selectedTab = AppTab.entries[selectedTabIndex]
    val localPlaylists = state.playlists.filter { it.kind == LibraryStore.LOCAL_KIND }
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        val message = state.message?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val duration = if (message.length > 28 || message.contains('\n')) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        Toast.makeText(context, message, duration).show()
        viewModel.consumeMessage(message)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = freshBackground(),
            bottomBar = {
                Column {
                    MiniPlayer(
                        state = state,
                        onExpand = { showPlayer = true },
                        onPrevious = viewModel::previous,
                        onToggle = viewModel::togglePlayback,
                        onNext = viewModel::next,
                    )
                    HearNavigationBar(
                        selectedTab = selectedTab,
                        onTabSelected = { tab ->
                            selectedTabIndex = AppTab.entries.indexOf(tab)
                            showLyricSettings = false
                        },
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (showLyricSettings) {
                    LyricsSettingsPage(
                        state = state,
                        onBack = { showLyricSettings = false },
                        onSettingChange = viewModel::updateLyricSetting,
                    )
                } else {
                    when (selectedTab) {
                        AppTab.Library -> LibraryPage(
                            state = state,
                            onSyncAll = viewModel::loadAllUserPlaylists,
                            onLocalNameChanged = viewModel::updateLocalPlaylistName,
                            onCreateLocalPlaylist = viewModel::createLocalPlaylist,
                            onOpenPlaylist = viewModel::openPlaylist,
                            onClosePlaylist = viewModel::closePlaylist,
                            onDeleteSelectedLocalPlaylist = viewModel::deleteSelectedLocalPlaylist,
                            onRemoveLocalTrack = viewModel::removeTrackFromSelectedLocalPlaylist,
                            onRequestAddToLocal = { trackForLocalPlaylist = it },
                            onPlayPlaylist = viewModel::playPlaylist,
                            onPlayTrack = viewModel::play,
                            onAddToQueue = viewModel::addToPlaybackQueue,
                        )

                        AppTab.Search -> SearchPage(
                            state = state,
                            onKeywordChanged = viewModel::updateKeyword,
                            onSearch = viewModel::search,
                            onPlayTrack = viewModel::play,
                            onRequestAddToLocal = { trackForLocalPlaylist = it },
                            onAddToQueue = viewModel::addToPlaybackQueue,
                            onLoadMore = viewModel::loadMoreSearchResults,
                        )

                        AppTab.Settings -> SettingsPage(
                            state = state,
                            cookieInputs = cookieInputs,
                            onSaveCookie = viewModel::saveCookie,
                            onClearCookie = viewModel::clearCookie,
                            onLoadUserPlaylists = viewModel::loadUserPlaylists,
                            onSyncAll = viewModel::loadAllUserPlaylists,
                            onImportPlaylist = viewModel::importPlaylist,
                            onPlaylistInputChanged = viewModel::updatePlaylistInput,
                            onOpenLyricSettings = { showLyricSettings = true },
                            onClearRemoteCache = viewModel::clearRemotePlaylistCache,
                            audioQuality = state.audioQuality,
                            onAudioQualityChange = { viewModel.setAudioQuality(it) },
                            sleepTimerMinutes = sleepTimerMinutes,
                            onSleepTimerChange = { minutes ->
                                sleepTimerMinutes = minutes
                                viewModel.setSleepTimer(minutes)
                            },
                            onExtractCookie = viewModel::extractCookieFromApp,
                            isShizukuAvailable = isShizukuAvailable,
                        )
                    }
                }
            }
        }

        if (showPlayer) {
            PlayerOverlay(
                state = state,
                onClose = { showPlayer = false },
                onPrevious = viewModel::previous,
                onToggle = viewModel::togglePlayback,
                onNext = viewModel::next,
                onSeek = viewModel::seekTo,
                onVolumeChange = viewModel::setVolume,
                onPlayModeChange = viewModel::setPlayMode,
                onPlayQueueItem = viewModel::playQueueItem,
                onRemoveQueueItem = viewModel::removeQueueItem,
                onClearQueue = viewModel::clearQueue,
            )
        }

        trackForLocalPlaylist?.let { track ->
            LocalPlaylistPickerDialog(
                track = track,
                playlists = localPlaylists,
                onDismiss = { trackForLocalPlaylist = null },
                onSelectPlaylist = { playlist ->
                    viewModel.addTrackToLocalPlaylist(playlist.id, track)
                    trackForLocalPlaylist = null
                },
                onCreateDefaultAndAdd = {
                    viewModel.addTrackToFirstLocalPlaylist(track)
                    trackForLocalPlaylist = null
                },
            )
        }
    }
}

@Composable
private fun HearNavigationBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    NavigationBar(containerColor = freshSurface()) {
        NavigationBarItem(
            selected = selectedTab == AppTab.Library,
            onClick = { onTabSelected(AppTab.Library) },
            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
            label = { Text("我的音乐") },
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.Search,
            onClick = { onTabSelected(AppTab.Search) },
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("聚合搜索") },
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.Settings,
            onClick = { onTabSelected(AppTab.Settings) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("设置") },
        )
    }
}
