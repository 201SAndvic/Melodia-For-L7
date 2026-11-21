package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.BuildConfig
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.R
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.theme.BottomSheetShape
import com.lin0721.linmusic.core.ui.theme.DragHandleShape
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.layout.MelodiaSheetDensity
import com.lin0721.linmusic.core.ui.layout.MelodiaSheetWidth
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsView(viewModel: SettingsViewModel) {
    // 渲染"关于"子设置项
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize()
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Melodia Player", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(appVersionLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 车机自用分支：整组「版本更新」入口已移除。
        // 提示升级装回的官方包会覆盖横屏/副屏适配与播放页封面改动，这里不再暴露任何检查/下载入口

        item {
            SettingsGroupCard("应用说明与协议") {
                Text(
                    text = "Melodia 是一款基于 Jetpack Compose 构建的第三方网易云音乐播放器。\n\n" +
                            "本项目基于开源协议发布。",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Start,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("开源协议 (MIT LICENSE)", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Start,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .padding(10.dp)
                    )
                }
            }
        }


        item {
            SettingsGroupCard("特别感谢") {
                Text(
                    text = "本项目的开发与运行离不开以下优秀开源项目：\n\n" +
                            "• NeteaseCloudMusicApi\n" +
                            "• NeteaseCloudMusicApiEnhanced\n" +
                            "• SPlayer\n" +
                            "• SuperLyric\n" +
                            "• LyricInfo\n" +
                            "• Lyricon\n" +
                            "• node-vibrant\n" +
                            "• Jetpack Compose & Media3\n" +
                            "• Retrofit & OkHttp\n" +
                            "• Koin\n" +
                            "• Coil\n" +
                            "• Haze",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Start,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

private const val TAG = "AboutSettingsScreen"

// tag 里的 -beta.N/-rc.N 后缀原样带进 versionName
private fun appVersionLabel(): String {
    val name = BuildConfig.VERSION_NAME
    val isPrerelease = name.contains("beta", ignoreCase = true) || name.contains("rc", ignoreCase = true)
    return if (isPrerelease) "Version $name（测试版）" else "Version $name"
}

internal fun logLevelLabel(level: AppLogger.LogLevel): String = when (level) {
    AppLogger.LogLevel.DEBUG -> "详细"
    AppLogger.LogLevel.INFO -> "标准"
    AppLogger.LogLevel.WARN -> "精简"
    AppLogger.LogLevel.ERROR -> "仅错误"
}

fun exportAndShareLogs(context: Context) {
    val logFiles = AppLogger.getLogFiles()
    if (logFiles.isEmpty()) {
        ToastManager.showToast("暂未产生诊断日志哦！")
        return
    }
    try {
        val authority = "${context.packageName}.fileprovider"
        val fileUris = ArrayList(logFiles.map { FileProvider.getUriForFile(context, authority, it) })
        val action = if (fileUris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        val shareIntent = Intent(action).apply {
            type = "text/plain"
            if (fileUris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, fileUris[0])
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
            }
            putExtra(Intent.EXTRA_SUBJECT, "Melodia 诊断日志反馈")
            putExtra(Intent.EXTRA_TEXT, "这是来自用户的 Melodia 诊断日志文件。")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // 授权目标 App 读取该 URI
        }
        context.startActivity(Intent.createChooser(shareIntent, "导出并提交日志"))
    } catch (e: Exception) {
        AppLogger.e(TAG, "日志导出分享失败", e)
        ToastManager.showToast("日志导出分享失败了...")
    }
}
