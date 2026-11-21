package com.lin0721.linmusic.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.log.PerformanceMonitor
import com.lin0721.linmusic.core.preferences.PerformanceOptimization
import com.lin0721.linmusic.core.preferences.PerformanceOptimizations
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettingsScreen(onBack: () -> Unit) {
    val preferences: SettingsPreferences = koinInject()
    val settings by preferences.performanceOptimizations.collectAsStateWithLifecycle(initialValue = PerformanceOptimizations())
    val scope = rememberCoroutineScope()

    @Composable
    fun OptimizationRow(option: PerformanceOptimization, title: String, description: String) {
        SettingsSwitchRow(title, description, settings.enabled(option)) { enabled ->
            scope.launch {
                preferences.savePerformanceOptimization(option, enabled)
                PerformanceMonitor.mark("performance_option", "key=${option.key} enabled=$enabled")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("性能优化", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { MelodiaIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        }, containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = MelodiaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)) {
            item {
                Text("各项独立开启，主副屏立即生效并记忆。开关开启表示启用该项优化，不影响音质和双屏。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                SettingsGroupCard("歌词与界面") {
                    OptimizationRow(PerformanceOptimization.LINE_HIGHLIGHT, "简化逐字歌词特效", "逐行高亮代替逐字擦除与光晕；保留歌词跟随和点击定位")
                    OptimizationRow(PerformanceOptimization.STATIC_LYRIC_SCALE, "关闭歌词缩放动画", "歌词保持固定大小；不影响字号与行间距设置")
                    OptimizationRow(PerformanceOptimization.SLOW_PROGRESS, "降低进度刷新频率", "歌词位置采样改为 200ms，普通进度显示减少刷新；不改音频播放速度")
                    OptimizationRow(PerformanceOptimization.SUSPEND_COVERED_PLAYER, "暂停隐藏播放页绘制", "全屏歌词打开时，不绘制其后方被遮挡的播放页；返回时恢复")
                    OptimizationRow(PerformanceOptimization.SIMPLE_DECORATIONS, "减少装饰动画", "停止播放条点状动画并简化歌词背景效果；封面取色保持不变")
                    OptimizationRow(PerformanceOptimization.DISABLE_COVER_GESTURE_ANIMATION, "关闭封面双屏手势动效", "取消开启/关闭副屏时的短暂封面位移；手势和状态提示仍有效")
                }
            }
            item {
                SettingsGroupCard("播放页信息加载") {
                    OptimizationRow(PerformanceOptimization.SKIP_COMMENTS, "暂停评论卡片加载", "暂时隐藏评论卡片并停止自动加载；关闭恢复原配置，不影响主动查看评论")
                    OptimizationRow(PerformanceOptimization.SKIP_WIKI, "暂停歌曲百科加载", "暂时隐藏歌曲百科卡片并停止自动加载；保留歌曲标题、歌手和歌词")
                    OptimizationRow(PerformanceOptimization.SKIP_ARTISTS, "暂停歌手详情加载", "暂时隐藏歌手资料、专辑和相似艺人卡片；关闭恢复原配置")
                }
            }
        }
    }
}
