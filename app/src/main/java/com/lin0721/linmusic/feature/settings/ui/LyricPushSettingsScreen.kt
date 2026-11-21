package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun LyricInfoEnabledRow(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    SettingsSwitchRow(
        title = "LyricInfo 系统歌词（测试）",
        subtitle = "通过系统媒体会话推送整首歌词，供 HUD 或其他兼容应用读取",
        checked = enabled,
        onCheckedChange = onEnabledChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricPushSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val enabled by viewModel.lyricInfoEnabled.collectAsStateWithLifecycle()
    val outputSettings by viewModel.lyricInfoOutputSettings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歌词推送", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    MelodiaIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = MelodiaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
        ) {
            item {
                SettingsGroupCard("推送开关") {
                    LyricInfoEnabledRow(enabled, viewModel::updateLyricInfoEnabled)
                }
            }
            item {
                LyricInfoOutputSettingsCard(outputSettings, viewModel::updateLyricInfoOutputSettings)
            }
        }
    }
}
