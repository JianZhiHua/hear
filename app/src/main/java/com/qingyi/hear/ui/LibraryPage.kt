package com.qingyi.hear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qingyi.hear.domain.Playlist
import com.qingyi.hear.domain.Track
import com.qingyi.hear.storage.LibraryStore

@Composable
internal fun LibraryPage(
    state: HearUiState,
    onSyncAll: () -> Unit,
    onLocalNameChanged: (String) -> Unit,
    onCreateLocalPlaylist: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onClosePlaylist: () -> Unit,
    onDeleteSelectedLocalPlaylist: () -> Unit,
    onRemoveLocalTrack: (Int) -> Unit,
    onRequestAddToLocal: (Track) -> Unit,
    onPlayPlaylist: (List<Track>) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
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
                    onPlayAll = { onPlayPlaylist(selectedTracks) },
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
                        onAddToLocal = { onRequestAddToLocal(track) },
                        onAddToQueue = { onAddToQueue(track) },
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
internal fun CacheSummary(
    playlistCount: Int,
    localCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(freshSurface())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.CloudDone, contentDescription = null, tint = freshMint())
        Text(
            text = "本机已缓存 $playlistCount 个歌单",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = freshLeaf(),
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
internal fun FreshHero(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = freshSurfaceSoft(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(freshSurface()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = freshLeaf())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = freshLeaf())
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun LocalPlaylistCreator(
    name: String,
    onNameChanged: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = freshSurface(),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = freshMint())
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
internal fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = freshSurface(),
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
internal fun PlaylistDetailHeader(
    playlist: Playlist,
    trackCount: Int,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onDeleteLocal: (() -> Unit)?,
) {
    Surface(
        color = freshSurface(),
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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(onClick = onPlayAll, enabled = trackCount > 0) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("全部播放")
                }
                if (onDeleteLocal != null) {
                    IconButton(onClick = onDeleteLocal) {
                        Icon(Icons.Default.Delete, contentDescription = "删除本地歌单", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
