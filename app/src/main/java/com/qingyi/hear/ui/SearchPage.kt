package com.qingyi.hear.ui

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingyi.hear.domain.Track

@Composable
internal fun SearchPage(
    state: HearUiState,
    onKeywordChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onRequestAddToLocal: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onLoadMore: (String) -> Unit,
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
        item {
            SourceSegment(
                selected = selectedSource,
                onSelected = { selectedSource = it },
                includeAll = false,
            )
        }
        if (results.isEmpty() && !state.isLoadingMoreSearch) {
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
                    onAddToLocal = { onRequestAddToLocal(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onPlay = { onPlayTrack(track) },
                    actionText = "播放",
                )
            }
            if (state.canLoadMoreSearch && selectedSource != "all") {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isLoadingMoreSearch) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("加载中...", style = MaterialTheme.typography.bodySmall)
                        } else {
                            TextButton(onClick = { onLoadMore(selectedSource) }) {
                                Text("加载更多")
                            }
                        }
                    }
                }
            }
        }
    }
}
