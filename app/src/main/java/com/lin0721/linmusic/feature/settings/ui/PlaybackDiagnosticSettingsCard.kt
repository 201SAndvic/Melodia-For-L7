package com.lin0721.linmusic.feature.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.core.log.PlaybackStutterDiagnostics
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.components.ToastManager
import kotlinx.coroutines.launch

@Composable
internal fun PlaybackDiagnosticSettingsCard(preferences: SettingsPreferences) {
    val context = LocalContext.current
    val enabled by preferences.playbackStutterDiagnosticsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val autoSave by PlaybackStutterDiagnostics.markerSaveStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun export(copy: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val text = PlaybackStutterDiagnostics.export()
                if (copy) {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Melodia播放卡顿诊断", text))
                    ToastManager.showToast("仅播放卡顿诊断已复制")
                } else saveDiagnosticText(context, text, "Melodia-播放卡顿")
            } catch (_: Exception) { ToastManager.showToast("播放诊断导出失败，请稍后重试") }
            finally { busy = false }
        }
    }
    SettingsGroupCard("播放卡顿诊断 · 独立记录") {
        SettingsSwitchRow("开启播放卡顿诊断", "默认关闭，与性能日志无关。记录缓冲、解码器、音频输出及设备变化、焦点和明显主线程延迟；关闭即停止。", enabled) { checked ->
            scope.launch { preferences.savePlaybackStutterDiagnosticsEnabled(checked) }
        }
        Text("开启后，听到断音只需点击下方标记，约6秒后自动保存到Download/Melodia，无需再点保存；记录开关保持开启。可离开本页，关闭记录会停止采样，但已标记的一次仍会保存已有内容。正常播放不逐秒写日志，不含歌词、播放链接或账号信息。",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsRow("记录当前卡顿并自动保存", if (autoSave.busy) autoSave.message else "需已开启；记录前10秒及后约5秒，随后自动保存，连续点击不会重复生成") {
            if (!enabled || !PlaybackStutterDiagnostics.enabled) ToastManager.showToast("请先开启播放卡顿诊断")
            else if (PlaybackStutterDiagnostics.markAndAutoSave()) ToastManager.showToast("已标记，约6秒后自动保存，可离开本页")
            else ToastManager.showToast("正在收集或保存上一条卡顿，请稍候")
        }
        if (autoSave.message.isNotBlank() && !autoSave.busy) Text(autoSave.message,
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsRow("保存播放诊断", if (busy) "正在导出" else "仅此诊断，保存到 Download/Melodia/Melodia-播放卡顿-日期.txt") { export(false) }
        SettingsRow("复制播放诊断", "仅复制这份精简记录，不混入原性能/应用日志") { export(true) }
        SettingsRow("清空播放诊断", "只清除此独立记录，不删除原日志；建议先关闭记录") { confirmClear = true }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清空播放诊断？") },
        text = { Text("只删除独立播放卡顿记录，无法恢复；请先保存需要反馈的记录。") },
        confirmButton = { TextButton(onClick = {
            confirmClear = false
            scope.launch {
                runCatching { PlaybackStutterDiagnostics.clear() }
                    .onSuccess { ToastManager.showToast("播放诊断已清空，其他日志不变") }
                    .onFailure { ToastManager.showToast("清空失败") }
            }
        }) { Text("清空") } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
}
