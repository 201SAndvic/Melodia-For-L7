package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.DualDisplayController
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences: SettingsPreferences = koinInject()
    val enabled by preferences.performanceLogEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("诊断与日志", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { MelodiaIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        }, containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = MelodiaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)) {
            item { PlaybackDiagnosticSettingsCard(preferences) }
            item {
                SettingsGroupCard("记录") {
                    SettingsSwitchRow("性能采样日志", "每 5 秒记录应用 CPU、内存、线程及双屏状态；关闭后停止", enabled) { checked ->
                        scope.launch {
                            preferences.savePerformanceLogEnabled(checked)
                            DualDisplayController.diagnosticSnapshot(context)
                        }
                    }
                }
            }
            item {
                SettingsGroupCard("反馈") {
                    SettingsRow("保存日志", "保存到 Download/Melodia，无需文件选择器") {
                        DualDisplayController.diagnosticSnapshot(context)
                        scope.launch { saveDiagnosticLogs(context) }
                    }
                    SettingsRow("分享日志", "分享最近诊断文本；没有分享应用时可复制") {
                        DualDisplayController.diagnosticSnapshot(context)
                        scope.launch { copyDiagnosticLogs(context, share = true) }
                    }
                    SettingsRow("复制日志", "便于粘贴反馈，不依赖分享应用") {
                        DualDisplayController.diagnosticSnapshot(context)
                        scope.launch { copyDiagnosticLogs(context) }
                    }
                    SettingsRow("清空日志", "清空前确认，避免误删诊断记录") { confirmClear = true }
                    Text("双屏状态自动随诊断日志记录，无需额外操作。", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清空日志？") },
        text = { Text("已记录的日志将无法恢复。请先保存需要反馈的日志。") },
        confirmButton = { TextButton(onClick = {
            confirmClear = false
            scope.launch {
                val cleared = withContext(Dispatchers.IO) { AppLogger.clearLogs() }
                ToastManager.showToast(if (cleared) "日志已清空" else "清空日志失败")
            }
        }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
    )
}
