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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyi.hear.domain.PlayMode
import com.qingyi.hear.domain.Track
import kotlin.math.abs

@Composable
internal fun PlayerOverlay(
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
            val targetIndex = (activeIndex - 2).coerceAtLeast(0)
            val distance = abs(listState.firstVisibleItemIndex - targetIndex)
            if (distance > 8) {
                listState.scrollToItem(targetIndex)
            } else {
                listState.animateScrollToItem(targetIndex)
            }
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
internal fun MiniPlayer(
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
