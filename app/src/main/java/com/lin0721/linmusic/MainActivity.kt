package com.lin0721.linmusic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.lin0721.linmusic.core.player.FloatingLyricService
import com.lin0721.linmusic.core.log.PerformanceMonitor
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.ui.theme.MelodiaTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val settingsPreferences: SettingsPreferences by inject()
    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }
    private var displayListenerRegistered = false
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            lifecycleScope.launch { startSecondaryIfEnabled() }
        }

        override fun onDisplayRemoved(displayId: Int) {
            if (DualDisplayController.activeDisplayId.value == displayId) DualDisplayController.stop()
        }

        override fun onDisplayChanged(displayId: Int) = Unit
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureMelodiaEdgeToEdge(window)
        // App 全局强制暗色主题，系统栏样式固定按暗色处理，不跟随系统亮暗色
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // 某些 Android 11 车机副屏会在 enableEdgeToEdge 后重新套回 decor fitting，
        // 明确保留布局到导航栏下方，避免副屏底部留下整块窗口背景色。
        configureMelodiaEdgeToEdge(window)
        requestNotificationPermissionIfNeeded()
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        displayListenerRegistered = true
        DualDisplayController.registerMain(this)

        // 监听悬浮歌词开关
        lifecycleScope.launch {
            settingsPreferences.showDesktopLrc.collectLatest { enabled ->
                if (enabled) {
                    startFloatingLyricOrRequestPermission()
                } else {
                    stopService(Intent(this@MainActivity, FloatingLyricService::class.java))
                }
            }
        }

        setContent {
            MelodiaTheme {
                MelodiaApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Reuse a visible secondary window when returning from another app. The
        // configured start page is only applied on creation or an explicit reset.
        if (displayListenerRegistered) lifecycleScope.launch { startSecondaryIfEnabled() }
    }

    private suspend fun startSecondaryIfEnabled() {
        settingsPreferences.migrateDualDisplayToPresentation()
        if (settingsPreferences.dualDisplayEnabled.first()) {
            PerformanceMonitor.mark(
                "dual_display_launch_request",
                "callerDisplay=${display?.displayId ?: -1} targetDisplay=${settingsPreferences.dualDisplayId.first()}"
            )
            DualDisplayController.launch(
                this,
                settingsPreferences.dualDisplayId.first(),
                settingsPreferences.dualDisplayContent.first() == "player"
            )
        }
    }


    override fun onDestroy() {
        PerformanceMonitor.mark("main_activity_destroyed", "display=${display?.displayId ?: -1} task=$taskId")
        if (displayListenerRegistered) displayManager.unregisterDisplayListener(displayListener)
        DualDisplayController.unregisterMain(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        PerformanceMonitor.mark("main_activity_resumed", "display=${display?.displayId ?: -1} task=$taskId")
        lifecycleScope.launch {
            val enabled = settingsPreferences.showDesktopLrc.first()
            if (enabled && android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                startService(Intent(this@MainActivity, FloatingLyricService::class.java))
            }
        }
    }

    // Android 13+ 需要运行时授权才能显示系统通知
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 有悬浮窗权限则直接启动桌面歌词，否则需要前往系统设置授权
    private fun startFloatingLyricOrRequestPermission() {
        if (android.provider.Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingLyricService::class.java))
            return
        }
        android.widget.Toast.makeText(
            this,
            "请开启悬浮窗权限以显示桌面歌词",
            android.widget.Toast.LENGTH_LONG
        ).show()
        startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
        )
    }
}

internal fun configureMelodiaEdgeToEdge(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}
