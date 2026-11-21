package com.lin0721.linmusic.feature.settings.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing

private fun resolveDisplayPath(context: android.content.Context, uriString: String?): String {
    if (uriString.isNullOrBlank()) {
        return "${Environment.getExternalStorageDirectory().absolutePath}/Music/Melodia"
    }
    val uri = Uri.parse(uriString)
    return runCatching {
        val treeDocId = DocumentsContract.getTreeDocumentId(uri)
        val (volumeId, relativePath) = treeDocId.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        if (volumeId == "primary") {
            "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath".trimEnd('/')
        } else {
            "$volumeId/$relativePath".trimEnd('/')
        }
    }.getOrElse {
        DocumentFile.fromTreeUri(context, uri)?.name ?: "自定义目录"
    }
}

private fun resolveInitialFolderUri(uriString: String?): Uri {
    if (!uriString.isNullOrBlank()) {
        return Uri.parse(uriString)
    }
    runCatching {
        val defaultDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Melodia")
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
    }
    return DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:Music/Melodia"
    )
}

@Composable
fun PlaybackDownloadSettingsView(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val autoPlayNext by viewModel.autoPlayNext.collectAsStateWithLifecycle()
    val playWithOtherApps by viewModel.playWithOtherApps.collectAsStateWithLifecycle()
    val audioOffload by viewModel.audioOffload.collectAsStateWithLifecycle()
    val offloadStatus by com.lin0721.linmusic.core.player.AudioOffloadRuntime.state.collectAsStateWithLifecycle()
    val streamCacheEnabled by viewModel.streamCacheEnabled.collectAsStateWithLifecycle()
    val weakNetworkCacheEnabled by viewModel.weakNetworkCacheEnabled.collectAsStateWithLifecycle()
    val downloadFolderUri by viewModel.downloadFolderUri.collectAsStateWithLifecycle()
    val downloadLyricsEnabled by viewModel.downloadLyricsEnabled.collectAsStateWithLifecycle()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        // 保留旧目录持久权限，避免已有下载文件丢失读取授权
        viewModel.updateDownloadFolderUri(uri.toString())
        ToastManager.showToast("下载目录已更新")
    }

    val downloadFolderSubtitle = remember(downloadFolderUri) {
        resolveDisplayPath(context, downloadFolderUri)
    }

    // 渲染播放与下载的子设置项
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        item {
            SettingsGroupCard("播放参数") {
                SettingsSwitchRow(
                    title = "自动播放推荐新歌",
                    subtitle = "当前曲目播放完毕后自动接入相似推荐",
                    checked = autoPlayNext,
                    onCheckedChange = { viewModel.updateAutoPlayNext(it) }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                SettingsSwitchRow(
                    title = "与其他应用同时播放",
                    subtitle = "开启后不被其他应用打断播放",
                    checked = playWithOtherApps,
                    onCheckedChange = { viewModel.updatePlayWithOtherApps(it) }
                )
            }
        }
        item {
            SettingsGroupCard("音频卸载 · 可选") {
                SettingsSwitchRow("硬件音频解码（音频卸载）", "默认关闭；系统和格式支持时交给音频硬件处理，不改变音质。播放中切换会等到暂停或下一首再应用。", audioOffload, viewModel::updateAudioOffload)
                val status = when {
                    offloadStatus.failed -> "应用失败，保留原播放路径；可关闭后重试"
                    offloadStatus.requested != offloadStatus.applied -> "设置待应用：请暂停或切到下一首"
                    !audioOffload -> "未请求音频卸载"
                    !offloadStatus.requested -> "等待播放器读取设置"
                    offloadStatus.active == true -> "当前已进入音频卸载" + if (offloadStatus.sleeping) "（卸载休眠中）" else ""
                    offloadStatus.active == false -> "当前使用普通播放路径" + if (offloadStatus.supported == false) "；系统报告该格式不支持卸载" else "；开启不保证实际卸载"
                    else -> "已请求，等待音频输出确认"
                }
                androidx.compose.material3.Text(status, fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.Text("不支持时允许回退；与交叉淡化不兼容，本分支没有启用双播放器淡化。主副屏共享同一音频引擎，实际收益需车机验证。", fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsGroupCard("下载与缓存") {
                SettingsSwitchRow(
                    title = "弱网缓存",
                    subtitle = "默认开启；缓存已浏览歌单、音乐库、歌词及播放音频，主副屏共用；不额外下载整首。音频容量在存储管理设置",
                    checked = weakNetworkCacheEnabled,
                    onCheckedChange = viewModel::updateWeakNetworkCacheEnabled
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                SettingsSwitchRow(
                    title = "边听边存",
                    subtitle = if (weakNetworkCacheEnabled) "弱网缓存启用时不另行下载；关闭弱网缓存后才按此项自动保存整首" else "在线播放时自动以当前音质保存歌曲到「边听边存」目录",
                    checked = streamCacheEnabled,
                    onCheckedChange = { viewModel.updateStreamCacheEnabled(it) }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                SettingsRow(
                    title = "下载目录",
                    subtitle = downloadFolderSubtitle,
                    onClick = {
                        val initialUri = resolveInitialFolderUri(downloadFolderUri)
                        folderPickerLauncher.launch(initialUri)
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                SettingsSwitchRow(
                    title = "下载歌词",
                    subtitle = "下载歌曲时额外保存一份同名 .lrc 歌词文件",
                    checked = downloadLyricsEnabled,
                    onCheckedChange = { viewModel.updateDownloadLyricsEnabled(it) }
                )
                if (!downloadFolderUri.isNullOrBlank()) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsRow(
                        title = "恢复默认下载目录",
                        subtitle = "改回 Music/Melodia",
                        onClick = {
                            viewModel.updateDownloadFolderUri(null)
                            ToastManager.showToast("已恢复默认下载目录")
                        }
                    )
                }
            }
        }
    }
}
