package com.lin0721.linmusic.feature.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.ui.components.ToastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private suspend fun logText(): String = withContext(Dispatchers.IO) {
    AppLogger.flush()
    AppLogger.getLogFiles().sortedBy { it.lastModified() }.joinToString("\n\n") {
        "--- ${it.name} ---\n" + it.readText()
    }.ifBlank { "尚无日志，请先记录双屏状态。" }
}

/** G636 has no ACTION_CREATE_DOCUMENT handler. Android 10+ Downloads needs no permission. */
internal suspend fun saveDiagnosticLogs(context: Context) {
    saveDiagnosticText(context, logText(), "Melodia-诊断")
}

/** Shared Downloads plumbing only. Caller supplies a wholly separate diagnostic text. */
internal suspend fun saveDiagnosticText(context: Context, text: String, prefix: String) {
    val result = withContext(Dispatchers.IO) {
        runCatching {
            com.lin0721.linmusic.core.log.writeDiagnosticText(context, text, prefix)
        }
    }
    result.onSuccess { ToastManager.showToast("日志已保存：$it") }
        .onFailure {
            AppLogger.e("DiagnosticExport", "保存失败", it)
            ToastManager.showToast("系统拒绝保存；请用复制日志或分享日志文本")
        }
}

internal suspend fun copyDiagnosticLogs(context: Context, share: Boolean = false) {
    // Stay below Android's Binder/clipboard transaction size. Preserve newest evidence.
    val full = logText()
    val text = if (full.length > 150_000) "[仅最近日志；完整日志请保存到下载目录]\n" + full.takeLast(150_000) else full
    if (share) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Melodia 双屏诊断")
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "分享日志文本").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { ToastManager.showToast("没有分享应用；请使用复制日志") }
    } else {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Melodia诊断", text))
        ToastManager.showToast("日志已复制")
    }
}
