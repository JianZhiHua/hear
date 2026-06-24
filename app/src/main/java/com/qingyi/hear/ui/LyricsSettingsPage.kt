package com.qingyi.hear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingyi.hear.domain.LyricBackgroundStyle
import com.qingyi.hear.domain.LyricColor
import com.qingyi.hear.domain.LyricSettings
import com.qingyi.hear.domain.LyricTextAlign

@Composable
internal fun LyricsSettingsPage(
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
