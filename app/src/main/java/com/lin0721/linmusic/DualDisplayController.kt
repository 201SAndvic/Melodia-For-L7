package com.lin0721.linmusic

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.lin0721.linmusic.core.ui.components.ToastManager
import java.lang.ref.WeakReference

data class MelodiaDisplay(val id: Int, val name: String, val width: Int, val height: Int, val densityDpi: Int)

/** One Activity and an independent passenger Presentation, sharing one player.
 * No startActivity, task reparenting or display-restore redirects. */
object DualDisplayController {
    private var host: WeakReference<MainActivity>? = null
    private var presentation: SecondaryPlayerPresentation? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val coverGestureMutex = Mutex()
    private val _activeDisplayId = MutableStateFlow<Int?>(null)
    val activeDisplayId = _activeDisplayId.asStateFlow()
    private val _routeStatus = MutableStateFlow("未打开副屏")
    val routeStatus = _routeStatus.asStateFlow()

    internal fun registerMain(activity: MainActivity) {
        if (host?.get() !== activity) stop()
        host = WeakReference(activity)
    }

    internal fun unregisterMain(activity: MainActivity) {
        if (host?.get() === activity) { stop(); host = null }
    }

    internal fun resolveDisplayId(preferredId: Int, availableIds: List<Int>): Int? =
        availableIds.firstOrNull { it == preferredId } ?: availableIds.firstOrNull()

    internal fun isPresentationCandidate(id: Int, name: String, flags: Int, valid: Boolean): Boolean =
        id != Display.DEFAULT_DISPLAY && valid && !name.contains("TaskVirtualDisplay") &&
            flags and Display.FLAG_PRESENTATION != 0 && flags and Display.FLAG_PRIVATE == 0

    fun availableDisplays(context: Context): List<MelodiaDisplay> =
        context.getSystemService(DisplayManager::class.java).displays
            .filter { isPresentationCandidate(it.displayId, it.name, it.flags, it.isValid) }
            .map {
                val m = DisplayMetrics()
                @Suppress("DEPRECATION")
                it.getRealMetrics(m)
                MelodiaDisplay(it.displayId, it.name, m.widthPixels, m.heightPixels, m.densityDpi)
            }

    fun launch(context: Context, preferredDisplayId: Int, openPlayer: Boolean, resetContent: Boolean = false): Boolean {
        val activity = host?.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed ||
            activity.window.decorView.display?.displayId != Display.DEFAULT_DISPLAY) {
            _routeStatus.value = "请在主屏打开 Melodia；不会移动现有窗口"
            return false
        }
        val targetId = resolveDisplayId(preferredDisplayId, availableDisplays(context).map { it.id })
        val target = activity.getSystemService(DisplayManager::class.java).getDisplay(targetId ?: -1)
        if (target == null) {
            _routeStatus.value = "未检测到支持独立窗口的副屏"
            return false
        }
        val current = presentation
        val action = secondaryLaunchAction(current?.display?.displayId, current?.isShowing == true, target.displayId, resetContent)
        if (action != SecondaryLaunchAction.CREATE && current != null) {
            if (action == SecondaryLaunchAction.RESET_CONTENT) current.refreshContent(openPlayer)
            AppLogger.performance("event=dual_display_existing action=$action targetDisplay=$targetId")
            diagnosticSnapshot(context)
            return true
        }
        stop()
        _routeStatus.value = "正在打开副屏独立窗口"
        diagnosticSnapshot(context)
        return try {
            val next = SecondaryPlayerPresentation(activity, target, openPlayer)
            presentation = next
            next.setOnDismissListener {
                if (presentation === next) {
                    presentation = null
                    _activeDisplayId.value = null
                    _routeStatus.value = "副屏窗口被关闭；可手动重新打开"
                    diagnosticSnapshot(context)
                }
                next.releaseOwners()
            }
            next.show()
            _activeDisplayId.value = targetId
            _routeStatus.value = "副屏独立窗口 · 屏幕 $targetId（主屏不迁移）"
            diagnosticSnapshot(context)
            true
        } catch (error: RuntimeException) {
            stop()
            _routeStatus.value = "副屏显示失败：${error.javaClass.simpleName}；已停用自动启动"
            AppLogger.e("DualDisplay", _routeStatus.value, error)
            scope.launch { SettingsPreferences(context.applicationContext).saveDualDisplayEnabled(false) }
            diagnosticSnapshot(context)
            false
        }
    }

    fun stop() {
        val old = presentation
        presentation = null
        _activeDisplayId.value = null
        _routeStatus.value = "副屏已关闭"
        old?.dismiss()
        old?.releaseOwners()
    }

    /** Application-scoped work survives dismissing the passenger composition. */
    fun setEnabledFromCover(context: Context, enabled: Boolean) {
        val preferences = SettingsPreferences(context.applicationContext)
        scope.launch {
          coverGestureMutex.withLock {
            if (!preferences.dualDisplayCoverGestureEnabled.first()) return@withLock
            if (enabled) {
                val opened = launch(context, preferences.dualDisplayId.first(), preferences.dualDisplayContent.first() == "player")
                if (opened) {
                    preferences.saveDualDisplayEnabled(true)
                    ToastManager.showToast("异屏同显已开启")
                } else ToastManager.showToast(routeStatus.value)
            } else {
                // Clear the persisted auto-start before releasing the visible window.
                preferences.saveDualDisplayEnabled(false)
                stop()
                ToastManager.showToast("异屏同显已关闭，音乐继续")
            }
            AppLogger.performance("event=dual_display_cover_gesture requestedEnabled=$enabled activeDisplay=${activeDisplayId.value}")
            diagnosticSnapshot(context)
          }
        }
    }

    fun diagnosticSnapshot(context: Context): String {
        val main = host?.get()
        val secondary = presentation
        val displays = context.getSystemService(DisplayManager::class.java).displays.joinToString {
            "${it.displayId}:${it.name}:flags=0x${it.flags.toString(16)}:valid=${it.isValid}"
        }
        val result = "version=${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE} route=Presentation" +
            " api=${android.os.Build.VERSION.SDK_INT} model=${android.os.Build.MODEL}" +
            " mainTask=${main?.taskId} mainDisplay=${main?.window?.decorView?.display?.displayId}" +
            " secondaryDisplay=${secondary?.display?.displayId} showing=${secondary?.isShowing}" +
            " secondaryViewDisplay=${secondary?.contentDisplayId} status=${_routeStatus.value} displays=[$displays]"
        AppLogger.performance("event=dual_display_snapshot $result")
        return result
    }
}
