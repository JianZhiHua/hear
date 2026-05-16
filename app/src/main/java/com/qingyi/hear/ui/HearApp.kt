package com.qingyi.hear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingyi.hear.domain.LyricBackgroundStyle
import com.qingyi.hear.domain.LyricColor
import com.qingyi.hear.domain.LyricSettings
import com.qingyi.hear.domain.LyricTextAlign
import com.qingyi.hear.domain.PlayMode
import com.qingyi.hear.domain.Playlist
import com.qingyi.hear.domain.Track
import com.qingyi.hear.storage.LibraryStore

private enum class AppTab {
    Library,
    Search,
    Settings,
}

private val FreshBackground = Color(0xFFF5FBF7)
private val FreshSurface = Color(0xFFFFFFFF)
private val FreshSurfaceSoft = Color(0xFFEAF7F0)
private val FreshMint = Color(0xFF62B58D)
private val FreshLeaf = Color(0xFF2D7A5A)
private val FreshPeach = Color(0xFFFFD8C7)

@Composable
fun HearApp(viewModel: HearViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cookieInputs = remember { mutableStateMapOf<String, String>() }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showLyricSettings by rememberSaveable { mutableStateOf(false) }
    val selectedTab = AppTab.entries[selectedTabIndex]

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = FreshBackground,
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
                            onPlayTrack = viewModel::play,
                        )

                        AppTab.Search -> SearchPage(
                            state = state,
                            onKeywordChanged = viewModel::updateKeyword,
                            onSearch = viewModel::search,
                            onPlayTrack = viewModel::play,
                            onAddToLocal = viewModel::addTrackToFirstLocalPlaylist,
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
    }
}

@Composable
private fun HearNavigationBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    NavigationBar(containerColor = FreshSurface) {
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

@Composable
private fun LibraryPage(
    state: HearUiState,
    onSyncAll: () -> Unit,
    onLocalNameChanged: (String) -> Unit,
    onCreateLocalPlaylist: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onClosePlaylist: () -> Unit,
    onDeleteSelectedLocalPlaylist: () -> Unit,
    onRemoveLocalTrack: (Int) -> Unit,
    onPlayTrack: (Track) -> Unit,
) {
    var sourceFilter by rememberSaveable { mutableStateOf("all") }
    val filteredPlaylists = state.playlists.filter { playlist ->
        sourceFilter == "all" || playlist.kind == sourceFilter
    }
    val selectedPlaylist = state.selectedPlaylist
    val selectedTracks = selectedPlaylist?.tracks?.takeIf { it.isNotEmpty() } ?: state.searchResults

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(
                title = "我的音乐",
                actionIcon = Icons.Default.Refresh,
                actionDescription = "手动刷新",
                onAction = onSyncAll,
            )
        }
        item { StatusBanner(state.message) }
        if (selectedPlaylist == null) {
            item {
                FreshHero(
                    title = "今天想听点什么",
                    body = "歌单会自动保存在本机，也可以把喜欢的歌收进本地歌单。",
                    icon = Icons.Default.CloudDone,
                )
            }
            item {
                CacheSummary(
                    playlistCount = state.playlists.size,
                    localCount = state.playlists.count { it.kind == LibraryStore.LOCAL_KIND },
                )
            }
            item {
                LocalPlaylistCreator(
                    name = state.localPlaylistName,
                    onNameChanged = onLocalNameChanged,
                    onCreate = onCreateLocalPlaylist,
                )
            }
            item {
                SourceSegment(
                    selected = sourceFilter,
                    onSelected = { sourceFilter = it },
                )
            }
            if (filteredPlaylists.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有同步歌单",
                        body = "在设置页填入 Cookie 后，点击右上角刷新即可同步两个平台的歌单。",
                    )
                }
            } else {
                itemsIndexed(
                    filteredPlaylists,
                    key = { index, playlist -> "${playlist.kind}:${playlist.id}:$index" },
                ) { _, playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist) },
                    )
                }
            }
        } else {
            item {
                PlaylistDetailHeader(
                    playlist = selectedPlaylist,
                    trackCount = selectedTracks.size,
                    onBack = onClosePlaylist,
                    onDeleteLocal = if (selectedPlaylist.kind == LibraryStore.LOCAL_KIND) onDeleteSelectedLocalPlaylist else null,
                )
            }
            if (selectedTracks.isEmpty()) {
                item { EmptyState(title = "歌单为空", body = "这个歌单暂时没有可展示的歌曲。") }
            } else {
                itemsIndexed(
                    selectedTracks,
                    key = { index, track -> "${track.source}:${track.id}:${track.resolverId}:$index" },
                ) { index, track ->
                    TrackListItem(
                        track = track,
                        index = index + 1,
                        active = track == state.currentTrack,
                        onPlay = { onPlayTrack(track) },
                        trailing = if (selectedPlaylist.kind == LibraryStore.LOCAL_KIND) {
                            {
                                IconButton(onClick = { onRemoveLocalTrack(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "从本地歌单移除")
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPage(
    state: HearUiState,
    onKeywordChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onAddToLocal: (Track) -> Unit,
) {
    var selectedSource by rememberSaveable { mutableStateOf("netease") }
    val results = state.searchResults.filter { it.source == selectedSource }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "聚合搜索",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.keyword,
                        onValueChange = onKeywordChanged,
                        modifier = Modifier.weight(1f),
                        label = { Text("输入歌曲名 / 歌手 / 专辑") },
                        singleLine = true,
                    )
                    FilledIconButton(onClick = onSearch, enabled = !state.isBusy) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            }
        }
        item { StatusBanner(state.message) }
        item {
            SourceSegment(
                selected = selectedSource,
                onSelected = { selectedSource = it },
                includeAll = false,
            )
        }
        if (results.isEmpty()) {
            item {
                EmptyState(
                    title = if (state.keyword.isBlank()) "输入关键词开始搜索" else "当前平台暂无结果",
                    body = "搜索结果会按平台隔离展示，避免同名歌曲混在一起。",
                )
            }
        } else {
            itemsIndexed(
                results,
                key = { index, track -> "${track.source}:${track.id}:${track.resolverId}:$index" },
            ) { index, track ->
                TrackListItem(
                    track = track,
                    index = index + 1,
                    active = track == state.currentTrack,
                    onPlay = { onPlayTrack(track) },
                    actionText = "播放",
                    trailing = {
                        IconButton(onClick = { onAddToLocal(track) }) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = "加入本地歌单")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(
    state: HearUiState,
    cookieInputs: MutableMap<String, String>,
    onSaveCookie: (String, String) -> Unit,
    onClearCookie: (String) -> Unit,
    onLoadUserPlaylists: (String) -> Unit,
    onSyncAll: () -> Unit,
    onImportPlaylist: (String) -> Unit,
    onPlaylistInputChanged: (String) -> Unit,
    onOpenLyricSettings: () -> Unit,
    onClearRemoteCache: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        item { StatusBanner(state.message) }
        item { SectionLabel("账号管理") }
        itemsIndexed(state.providers, key = { _, provider -> provider.source }) { _, provider ->
            AccountCard(
                provider = provider,
                cookie = cookieInputs[provider.source].orEmpty(),
                busy = state.isBusy,
                onCookieChanged = { cookieInputs[provider.source] = it },
                onSaveCookie = { onSaveCookie(provider.source, cookieInputs[provider.source].orEmpty()) },
                onClearCookie = { onClearCookie(provider.source) },
                onLoadUserPlaylists = { onLoadUserPlaylists(provider.source) },
            )
        }
        item {
            Button(
                onClick = onSyncAll,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("校验并同步所有歌单")
            }
        }
        item {
            ManualPlaylistImport(
                state = state,
                onPlaylistInputChanged = onPlaylistInputChanged,
                onImportPlaylist = onImportPlaylist,
            )
        }
        item { SectionLabel("个性化") }
        item {
            SettingsRow(
                icon = Icons.Default.Tune,
                title = "歌词排版设置",
                subtitle = "字号、颜色、对齐、行距、背景",
                onClick = onOpenLyricSettings,
            )
        }
        item {
            SettingsRow(
                icon = Icons.Default.Palette,
                title = "主题模式",
                subtitle = "跟随系统",
                onClick = null,
            )
        }
        item { SectionLabel("关于") }
        item {
            SettingsRow(
                icon = Icons.Default.CloudDone,
                title = "歌单缓存",
                subtitle = "已缓存 ${state.playlists.count { it.kind != LibraryStore.LOCAL_KIND }} 个平台歌单",
                onClick = onClearRemoteCache,
            )
        }
        item {
            SettingsRow(
                icon = Icons.Default.MusicNote,
                title = "版本号",
                subtitle = "V1.0.0",
                onClick = null,
            )
        }
    }
}

@Composable
private fun LyricsSettingsPage(
    state: HearUiState,
    onBack: () -> Unit,
    onSettingChange: (LyricSettings) -> Unit,
) {
    val settings = state.lyricSettings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "歌词设置",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        LyricPreview(settings)
        SettingSlider(
            label = "字号 ${settings.fontSizeSp.toInt()}sp",
            value = settings.fontSizeSp,
            range = 12f..48f,
            onValueChange = { onSettingChange(settings.copy(fontSizeSp = it)) },
        )
        SettingSlider(
            label = "行距 %.1f".format(settings.lineSpacing),
            value = settings.lineSpacing,
            range = 1.0f..2.0f,
            onValueChange = { onSettingChange(settings.copy(lineSpacing = it)) },
        )
        OptionGroup("对齐形式") {
            LyricTextAlign.entries.forEach { alignment ->
                SegmentButton(
                    text = alignment.label(),
                    selected = settings.alignment == alignment,
                    onClick = { onSettingChange(settings.copy(alignment = alignment)) },
                )
            }
        }
        OptionGroup("歌词主题") {
            LyricColor.entries.forEach { color ->
                SegmentButton(
                    text = color.label(),
                    selected = settings.color == color,
                    onClick = { onSettingChange(settings.copy(color = color)) },
                )
            }
        }
        OptionGroup("背景效果") {
            LyricBackgroundStyle.entries.forEach { background ->
                SegmentButton(
                    text = background.label(),
                    selected = settings.backgroundStyle == background,
                    onClick = { onSettingChange(settings.copy(backgroundStyle = background)) },
                )
            }
        }
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存并应用")
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun PlayerOverlay(
    state: HearUiState,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlayModeChange: (PlayMode) -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(playerBackgroundBrush(state.currentTrack)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                PlayerTopBar(
                    state = state,
                    page = page,
                    onPageChange = { page = it },
                    onClose = onClose,
                )
                when (page) {
                    0 -> CoverPlayerPage(
                        state = state,
                        onPrevious = onPrevious,
                        onToggle = onToggle,
                        onNext = onNext,
                        onSeek = onSeek,
                        onPlayModeChange = onPlayModeChange,
                    )

                    1 -> ImmersiveLyricsPage(state = state)
                    else -> QueuePage(
                        state = state,
                        onPlayQueueItem = onPlayQueueItem,
                        onRemoveQueueItem = onRemoveQueueItem,
                        onClearQueue = onClearQueue,
                    )
                }
                if (page == 1) {
                    VolumeControl(
                        volume = state.volume,
                        onVolumeChange = onVolumeChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    state: HearUiState,
    page: Int,
    onPageChange: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = state.currentTrack?.source?.let(::platformName).orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            FilledIconButton(onClick = { onPageChange(2) }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "播放队列")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentButton("封面", page == 0) { onPageChange(0) }
            SegmentButton("歌词", page == 1) { onPageChange(1) }
            SegmentButton("队列", page == 2) { onPageChange(2) }
        }
    }
}

@Composable
private fun CoverPlayerPage(
    state: HearUiState,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayModeChange: (PlayMode) -> Unit,
) {
    val track = state.currentTrack
    val duration = effectiveDuration(state)
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        ArtworkTile(
            source = track?.source,
            title = track?.title ?: "听见",
            size = 280.dp,
            rounded = 20.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = track?.title ?: "未播放",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = listOfNotNull(track?.displayArtist, track?.album).joinToString(" - ").ifBlank { "选择一首歌开始播放" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Slider(
                value = state.positionMs.coerceIn(0L, duration.coerceAtLeast(0L)).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                enabled = duration > 0L && track != null,
            )
            Row {
                Text(formatClock(state.positionMs), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(formatClock(duration), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onPlayModeChange(state.playMode.next()) }) {
                Icon(state.playMode.icon(), contentDescription = state.playMode.label())
            }
            IconButton(onClick = onPrevious, enabled = state.queue.isNotEmpty()) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(34.dp))
            }
            FilledIconButton(
                onClick = onToggle,
                enabled = track != null,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onNext, enabled = state.queue.isNotEmpty()) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(34.dp))
            }
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ImmersiveLyricsPage(state: HearUiState) {
    val listState = rememberLazyListState()
    val activeIndex = state.activeLyricIndex
    LaunchedEffect(activeIndex, state.lyricLines.size) {
        if (activeIndex != null && activeIndex in state.lyricLines.indices) {
            listState.animateScrollToItem((activeIndex - 3).coerceAtLeast(0))
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        color = Color.Transparent,
    ) {
        if (state.lyricLines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.lyricStatus ?: "暂无歌词",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 120.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                itemsIndexed(state.lyricLines, key = { index, line -> "${line.timeMs}:$index:${line.text}" }) { index, line ->
                    val active = index == state.activeLyricIndex
                    Text(
                        text = line.text,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (active) lyricActiveColor(state.lyricSettings.color) else lyricNormalColor(state.lyricSettings.color).copy(alpha = 0.48f),
                        fontSize = (if (active) state.lyricSettings.fontSizeSp + 4f else state.lyricSettings.fontSizeSp).sp,
                        lineHeight = (state.lyricSettings.fontSizeSp * state.lyricSettings.lineSpacing).sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        textAlign = state.lyricSettings.alignment.toTextAlign(),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueuePage(
    state: HearUiState,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "播放队列",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClearQueue, enabled = state.queue.isNotEmpty()) {
                Text("清空")
            }
        }
        if (state.queue.isEmpty()) {
            EmptyState(title = "队列为空", body = "从搜索结果或歌单里选择一首歌即可开始。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(state.queue, key = { index, track -> "${track.source}:${track.id}:$index" }) { index, track ->
                    TrackListItem(
                        track = track,
                        index = index + 1,
                        active = index == state.currentIndex,
                        onPlay = { onPlayQueueItem(index) },
                        trailing = {
                            IconButton(onClick = { onRemoveQueueItem(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "从队列移除")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    state: HearUiState,
    onExpand: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    val track = state.currentTrack
    val duration = effectiveDuration(state)
    val progress = if (duration > 0L) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Surface(tonalElevation = 4.dp) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpand() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ArtworkTile(source = track?.source, title = track?.title ?: "听见", size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: "未播放",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = track?.displayArtist ?: if (state.isBuffering) "正在缓冲" else "选择歌曲开始播放",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPrevious, enabled = state.queue.isNotEmpty()) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一首")
                }
                IconButton(onClick = onToggle, enabled = track != null) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                    )
                }
                IconButton(onClick = onNext, enabled = state.queue.isNotEmpty()) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首")
                }
            }
        }
    }
}

@Composable
private fun PageHeader(
    title: String,
    actionIcon: ImageVector,
    actionDescription: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        FilledIconButton(onClick = onAction) {
            Icon(actionIcon, contentDescription = actionDescription)
        }
    }
}

@Composable
private fun StatusBanner(message: String?) {
    if (message.isNullOrBlank()) return
    Surface(
        color = FreshSurfaceSoft,
        contentColor = FreshLeaf,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CacheSummary(
    playlistCount: Int,
    localCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(FreshSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.CloudDone, contentDescription = null, tint = FreshMint)
        Text(
            text = "本机已缓存 $playlistCount 个歌单",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = FreshLeaf,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "$localCount 个本地",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FreshHero(
    title: String,
    body: String,
    icon: ImageVector,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FreshSurfaceSoft,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(FreshSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = FreshLeaf)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FreshLeaf)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LocalPlaylistCreator(
    name: String,
    onNameChanged: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FreshSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = FreshMint)
            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                modifier = Modifier.weight(1f),
                label = { Text("新建本地歌单") },
                singleLine = true,
            )
            FilledIconButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "创建本地歌单")
            }
        }
    }
}

@Composable
private fun SourceSegment(
    selected: String,
    onSelected: (String) -> Unit,
    includeAll: Boolean = true,
) {
    val options = buildList {
        if (includeAll) add("all" to "全部")
        if (includeAll) add(LibraryStore.LOCAL_KIND to "本地")
        add("netease" to "网易云音乐")
        add("qq" to "QQ 音乐")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            SegmentButton(
                text = label,
                selected = selected == value,
                onClick = { onSelected(value) },
            )
        }
    }
}

@Composable
private fun SegmentButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text) }
    }
}

@Composable
private fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = FreshSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkTile(source = playlist.kind, title = playlist.name, size = 64.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${platformName(playlist.kind)} · ${playlist.trackCount ?: playlist.tracks.size} 首",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PlaylistDetailHeader(
    playlist: Playlist,
    trackCount: Int,
    onBack: () -> Unit,
    onDeleteLocal: (() -> Unit)?,
) {
    Surface(
        color = FreshSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回歌单列表")
            }
            ArtworkTile(source = playlist.kind, title = playlist.name, size = 76.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${platformName(playlist.kind)} · $trackCount 首",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDeleteLocal != null) {
                IconButton(onClick = onDeleteLocal) {
                    Icon(Icons.Default.Delete, contentDescription = "删除本地歌单", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TrackListItem(
    track: Track,
    index: Int,
    active: Boolean,
    onPlay: () -> Unit,
    actionText: String = if (active) "重播" else "播放",
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else FreshSurface,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (active) 2.dp else 1.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = index.toString(),
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ArtworkTile(source = track.source, title = track.title, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = trackSubtitle(track),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onPlay) {
                Text(actionText)
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun AccountCard(
    provider: ProviderStatus,
    cookie: String,
    busy: Boolean,
    onCookieChanged: (String) -> Unit,
    onSaveCookie: () -> Unit,
    onClearCookie: () -> Unit,
    onLoadUserPlaylists: () -> Unit,
) {
    Surface(
        color = FreshSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(active = provider.hasCookie)
                Spacer(Modifier.width(8.dp))
                Text(provider.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (provider.hasCookie) "已连接" else "未配置",
                    color = if (provider.hasCookie) Color(0xFF1B7F47) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedTextField(
                value = cookie,
                onValueChange = onCookieChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("粘贴 Cookie") },
                visualTransformation = PasswordVisualTransformation(),
                minLines = 1,
                maxLines = 3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveCookie, enabled = !busy) {
                    Text("校验并保存")
                }
                OutlinedButton(onClick = onLoadUserPlaylists, enabled = !busy && provider.hasCookie) {
                    Text("同步歌单")
                }
                TextButton(onClick = onClearCookie, enabled = !busy && provider.hasCookie) {
                    Text("清除")
                }
            }
        }
    }
}

@Composable
private fun ManualPlaylistImport(
    state: HearUiState,
    onPlaylistInputChanged: (String) -> Unit,
    onImportPlaylist: (String) -> Unit,
) {
    Surface(
        color = FreshSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("手动导入歌单", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.playlistInput,
                onValueChange = onPlaylistInputChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("歌单 ID 或链接") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onImportPlaylist("netease") }, enabled = !state.isBusy) {
                    Text("网易云导入")
                }
                OutlinedButton(onClick = { onImportPlaylist("qq") }, enabled = !state.isBusy) {
                    Text("QQ 音乐导入")
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = FreshSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) {
                Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LyricPreview(settings: LyricSettings) {
    Surface(
        color = lyricBackgroundColor(settings.backgroundStyle),
        contentColor = lyricNormalColor(settings.color),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .height(180.dp)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "此时此地 阳光普照",
                modifier = Modifier.fillMaxWidth(),
                color = lyricActiveColor(settings.color),
                fontSize = settings.fontSizeSp.sp,
                lineHeight = (settings.fontSizeSp * settings.lineSpacing).sp,
                fontWeight = FontWeight.Bold,
                textAlign = settings.alignment.toTextAlign(),
            )
            Text(
                text = "这样的恩赐算不算太少",
                modifier = Modifier.fillMaxWidth(),
                color = lyricNormalColor(settings.color).copy(alpha = 0.62f),
                fontSize = settings.fontSizeSp.sp,
                lineHeight = (settings.fontSizeSp * settings.lineSpacing).sp,
                textAlign = settings.alignment.toTextAlign(),
            )
        }
    }
}

@Composable
private fun VolumeControl(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "音量")
        Slider(
            value = volume.coerceIn(0f, 1f),
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Text("${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

@Composable
private fun OptionGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@Composable
private fun ArtworkTile(
    source: String?,
    title: String,
    size: Dp,
    rounded: Dp = 8.dp,
) {
    val color = platformColor(source)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(rounded))
            .background(
                Brush.linearGradient(
                    colors = listOf(color.copy(alpha = 0.92f), MaterialTheme.colorScheme.tertiaryContainer),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = source?.let(::platformShortName) ?: title.take(1).ifBlank { "听" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FreshSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun StatusDot(active: Boolean) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFF1B7F47) else MaterialTheme.colorScheme.error),
    )
}

@Composable
private fun playerBackgroundBrush(track: Track?): Brush {
    val sourceColor = platformColor(track?.source).copy(alpha = 0.18f)
    return Brush.verticalGradient(
        colors = listOf(
            sourceColor,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun platformColor(source: String?): Color =
    when (source) {
        "qq" -> Color(0xFF1FA463)
        "netease" -> Color(0xFFD6453D)
        LibraryStore.LOCAL_KIND -> FreshMint
        else -> MaterialTheme.colorScheme.primary
    }

@Composable
private fun lyricBackgroundColor(style: LyricBackgroundStyle): Color =
    when (style) {
        LyricBackgroundStyle.Plain -> MaterialTheme.colorScheme.surface
        LyricBackgroundStyle.Surface -> MaterialTheme.colorScheme.surfaceVariant
        LyricBackgroundStyle.Dark -> Color(0xFF111318)
    }

@Composable
private fun lyricNormalColor(color: LyricColor): Color =
    when (color) {
        LyricColor.Primary -> MaterialTheme.colorScheme.onSurfaceVariant
        LyricColor.Light -> MaterialTheme.colorScheme.onSurface
        LyricColor.Warm -> Color(0xFF9B5D00)
        LyricColor.Green -> Color(0xFF1B7F47)
    }

@Composable
private fun lyricActiveColor(color: LyricColor): Color =
    when (color) {
        LyricColor.Primary -> MaterialTheme.colorScheme.primary
        LyricColor.Light -> MaterialTheme.colorScheme.onSurface
        LyricColor.Warm -> Color(0xFFE07A00)
        LyricColor.Green -> Color(0xFF00A05A)
    }

private fun trackSubtitle(track: Track): String =
    buildList {
        add(track.displayArtist)
        track.album?.takeIf(String::isNotBlank)?.let(::add)
        formatDuration(track.durationMs)?.let(::add)
        add(platformName(track.source))
    }.joinToString(" · ")

private fun effectiveDuration(state: HearUiState): Long =
    state.durationMs.takeIf { it > 0L } ?: state.currentTrack?.durationMs ?: 0L

private fun LyricTextAlign.toTextAlign(): TextAlign =
    when (this) {
        LyricTextAlign.Start -> TextAlign.Start
        LyricTextAlign.Center -> TextAlign.Center
        LyricTextAlign.End -> TextAlign.End
    }

private fun LyricColor.label(): String =
    when (this) {
        LyricColor.Primary -> "主题"
        LyricColor.Light -> "纯白"
        LyricColor.Warm -> "柔和"
        LyricColor.Green -> "霓虹"
    }

private fun LyricTextAlign.label(): String =
    when (this) {
        LyricTextAlign.Start -> "左对齐"
        LyricTextAlign.Center -> "居中"
        LyricTextAlign.End -> "右对齐"
    }

private fun LyricBackgroundStyle.label(): String =
    when (this) {
        LyricBackgroundStyle.Plain -> "纯净白"
        LyricBackgroundStyle.Surface -> "柔和"
        LyricBackgroundStyle.Dark -> "深邃黑"
    }

private fun PlayMode.label(): String =
    when (this) {
        PlayMode.Order -> "顺序播放"
        PlayMode.Single -> "单曲循环"
        PlayMode.Shuffle -> "随机播放"
    }

private fun PlayMode.next(): PlayMode =
    when (this) {
        PlayMode.Order -> PlayMode.Single
        PlayMode.Single -> PlayMode.Shuffle
        PlayMode.Shuffle -> PlayMode.Order
    }

private fun PlayMode.icon(): ImageVector =
    when (this) {
        PlayMode.Order -> Icons.AutoMirrored.Filled.QueueMusic
        PlayMode.Single -> Icons.Default.RepeatOne
        PlayMode.Shuffle -> Icons.Default.Shuffle
    }

private fun platformName(source: String): String =
    when (source) {
        "qq" -> "QQ 音乐"
        "netease" -> "网易云音乐"
        LibraryStore.LOCAL_KIND -> "本地歌单"
        else -> source
    }

private fun platformShortName(source: String): String =
    when (source) {
        "qq" -> "QQ"
        "netease" -> "网易云"
        LibraryStore.LOCAL_KIND -> "本地"
        else -> source.uppercase().take(2)
    }

private fun formatDuration(durationMs: Long?): String? {
    val totalSeconds = durationMs?.div(1000) ?: return null
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatClock(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
