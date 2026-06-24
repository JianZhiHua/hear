package com.qingyi.hear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingyi.hear.domain.AudioQuality
import com.qingyi.hear.network.UpdateResult
import com.qingyi.hear.storage.LibraryStore

@Composable
internal fun SettingsPage(
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
    audioQuality: AudioQuality,
    onAudioQualityChange: (AudioQuality) -> Unit,
    sleepTimerMinutes: Int,
    onSleepTimerChange: (Int) -> Unit,
    onExtractCookie: (String) -> Unit,
    isShizukuAvailable: Boolean,
    onCheckUpdate: () -> Unit,
    onUpdateResultConsumed: () -> Unit,
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
                onExtractCookie = if (isShizukuAvailable) {
                    { onExtractCookie(provider.source) }
                } else null,
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
            AudioQualitySelector(
                current = audioQuality,
                onSelect = onAudioQualityChange,
            )
        }
        item {
            SleepTimerSetting(
                currentMinutes = sleepTimerMinutes,
                onChange = onSleepTimerChange,
            )
        }
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
            val context = LocalContext.current
            val versionName = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
            SettingsRow(
                icon = Icons.Default.MusicNote,
                title = "版本号",
                subtitle = if (state.isCheckingUpdate) "检查中..." else "V$versionName",
                onClick = if (state.isCheckingUpdate) null else onCheckUpdate,
            )
        }
    }

    // 更新对话框
    val updateResult = state.updateResult
    if (updateResult is UpdateResult.Available) {
        UpdateAvailableDialog(
            version = updateResult.version,
            body = updateResult.body,
            url = updateResult.url,
            onDismiss = onUpdateResultConsumed,
        )
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
    onExtractCookie: (() -> Unit)?,
) {
    Surface(
        color = freshSurface(),
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
                if (onExtractCookie != null) {
                    OutlinedButton(onClick = onExtractCookie, enabled = !busy) {
                        Text("自动提取")
                    }
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
        color = freshSurface(),
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
private fun AudioQualitySelector(
    current: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = freshSurface(),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("音质选择", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(current.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioQuality.entries.forEach { quality ->
                    FilterChip(
                        selected = current == quality,
                        onClick = { onSelect(quality) },
                        label = { Text(quality.displayName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepTimerSetting(
    currentMinutes: Int,
    onChange: (Int) -> Unit,
) {
    val presets = listOf(15, 30, 45, 60, 0)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = freshSurface(),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("定时停止", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (currentMinutes > 0) {
                    Text("${currentMinutes} 分钟后", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("未开启", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { minutes ->
                    FilterChip(
                        selected = currentMinutes == minutes,
                        onClick = { onChange(minutes) },
                        label = {
                            Text(if (minutes == 0) "关闭" else "${minutes}分钟")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableDialog(
    version: String,
    body: String,
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "发现新版本",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "新版本：V$version",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (body.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                    ) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url),
                            )
                            context.startActivity(intent)
                            onDismiss()
                        },
                    ) {
                        Text("前往下载")
                    }
                }
            }
        }
    }
}
