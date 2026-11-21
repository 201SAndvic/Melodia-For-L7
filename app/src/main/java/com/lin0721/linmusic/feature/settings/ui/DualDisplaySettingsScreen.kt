package com.lin0721.linmusic.feature.settings.ui

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.DualDisplayController
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.MelodiaDisplay
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.components.MelodiaIconButton
import com.lin0721.linmusic.core.ui.components.ToastManager
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualDisplaySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences: SettingsPreferences = koinInject()
    val enabled by preferences.dualDisplayEnabled.collectAsStateWithLifecycle(initialValue = false)
    val coverGestureEnabled by preferences.dualDisplayCoverGestureEnabled.collectAsStateWithLifecycle(initialValue = false)
    val selectedId by preferences.dualDisplayId.collectAsStateWithLifecycle(initialValue = 1)
    val content by preferences.dualDisplayContent.collectAsStateWithLifecycle(initialValue = "player")
    val activeDisplayId by DualDisplayController.activeDisplayId.collectAsStateWithLifecycle()
    val routeStatus by DualDisplayController.routeStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var displays by remember { mutableStateOf(DualDisplayController.availableDisplays(context)) }
    val displayManager = remember(context) { context.getSystemService(DisplayManager::class.java) }

    DisposableEffect(displayManager) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { displays = DualDisplayController.availableDisplays(context) }
            override fun onDisplayRemoved(displayId: Int) { displays = DualDisplayController.availableDisplays(context) }
            override fun onDisplayChanged(displayId: Int) { displays = DualDisplayController.availableDisplays(context) }
        }
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { displayManager.unregisterDisplayListener(listener) }
    }

    fun openOnSelectedDisplay(id: Int = selectedId, showPlayer: Boolean = content == "player", resetContent: Boolean = false): Boolean {
        val opened = DualDisplayController.launch(context, id, showPlayer, resetContent)
        if (!opened) ToastManager.showToast(DualDisplayController.routeStatus.value)
        return opened
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("异屏同显", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    MelodiaIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = MelodiaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MelodiaSpacing.md),
            contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
        ) {
            item {
                SettingsGroupCard("双屏显示") {
                    SettingsSwitchRow(
                        title = "启用异屏同显",
                        subtitle = "副屏使用独立窗口，不移动主屏；共用当前播放",
                        checked = enabled,
                        onCheckedChange = { checked ->
                            scope.launch {
                                if (checked && displays.isEmpty()) {
                                    ToastManager.showToast("未检测到副屏")
                                } else {
                                    if (checked) {
                                        // Save enabled only after the independent window opened.
                                        preferences.saveDualDisplayEnabled(openOnSelectedDisplay())
                                    } else {
                                        preferences.saveDualDisplayEnabled(false)
                                        DualDisplayController.stop()
                                    }
                                }
                            }
                        }
                    )
                    Text(
                        "副屏启动内容",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                    )
                    Text(
                        "首次打开副屏时应用；主屏返回时保留副屏当前页面",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    DualContentOption(
                        title = "播放页",
                        subtitle = "封面、播放控件和歌词",
                        selected = content == "player",
                        onClick = {
                            scope.launch {
                                preferences.saveDualDisplayContent("player")
                                if (enabled) openOnSelectedDisplay(showPlayer = true, resetContent = true)
                            }
                        }
                    )
                    DualContentOption(
                        title = "主页",
                        subtitle = "副屏启动时显示 Melodia 主页",
                        selected = content == "home",
                        onClick = {
                            scope.launch {
                                preferences.saveDualDisplayContent("home")
                                if (enabled) openOnSelectedDisplay(showPlayer = false, resetContent = true)
                            }
                        }
                    )
                }
            }
            item {
                SettingsGroupCard("封面手势") {
                    SettingsSwitchRow("封面上下滑控制双屏", "默认关闭；封面上滑启用并显示副屏，下滑关闭双屏，音乐继续；左右滑仍切歌", coverGestureEnabled) { checked ->
                        scope.launch { preferences.saveDualDisplayCoverGestureEnabled(checked) }
                    }
                }
            }
            item {
                SettingsGroupCard("目标屏幕") {
                    if (displays.isEmpty()) {
                        Text(
                            "未检测到外接屏幕",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp)
                        )
                    } else {
                        displays.forEach { display ->
                            DisplayOption(display, selectedId == display.id) {
                                scope.launch {
                                    preferences.saveDualDisplayId(display.id)
                                    if (enabled) {
                                        DualDisplayController.stop()
                                        openOnSelectedDisplay(display.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingsGroupCard("连接状态") {
                    val active = displays.firstOrNull { it.id == activeDisplayId }
                    Text(
                        if (active != null) "正在显示：${active.name} · ${active.width} × ${active.height}"
                        else routeStatus,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { openOnSelectedDisplay() },
                            enabled = enabled && displays.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("打开副屏") }
                        Button(
                            onClick = { DualDisplayController.stop() },
                            enabled = activeDisplayId != null,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) { Text("关闭副屏") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayOption(display: MelodiaDisplay, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().pressable(MelodiaPress.Row, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(MelodiaSpacing.sm))
        Column {
            Text("${display.name} · 屏幕 ${display.id}", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            Text("${display.width} × ${display.height} · ${display.densityDpi} dpi", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DualContentOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().pressable(MelodiaPress.Row, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(MelodiaSpacing.sm))
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
