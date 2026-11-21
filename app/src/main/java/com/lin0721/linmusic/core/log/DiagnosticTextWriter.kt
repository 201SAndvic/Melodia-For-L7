package com.lin0721.linmusic.core.log

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared storage only: callers provide independent text; never reads general logs. */
internal suspend fun writeDiagnosticText(context: Context, text: String, prefix: String): String = withContext(Dispatchers.IO) {
    val name = "$prefix-${SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())}.txt"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Melodia")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("系统未提供下载目录")
        try {
            resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) } ?: error("无法写入日志")
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) > 0) { "无法完成日志保存" }
            "Download/Melodia/$name"
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    } else {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, name)
        file.writeText(text)
        file.absolutePath
    }
}
