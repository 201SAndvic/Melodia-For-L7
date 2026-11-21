package com.lin0721.linmusic

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lin0721.linmusic.core.download.DownloadWorkerFactory
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.log.CrashHandler
import com.lin0721.linmusic.core.log.PerformanceMonitor
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.di.downloadModule
import com.lin0721.linmusic.di.localModule
import com.lin0721.linmusic.di.localMusicModule
import com.lin0721.linmusic.di.networkModule
import com.lin0721.linmusic.di.playerModule
import com.lin0721.linmusic.di.repositoryModule
import com.lin0721.linmusic.di.updateModule
import com.lin0721.linmusic.di.viewModelModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.android.ext.android.inject
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MelodiaApplication : Application() {

    private val downloadWorkerFactory: DownloadWorkerFactory by inject()

    override fun onCreate() {
        super.onCreate()
        // 尽早初始化，覆盖 Koin/Coil 启动阶段的崩溃与日志
        AppLogger.init(this)
        CrashHandler.init(this)
        PerformanceMonitor.start(SettingsPreferences(this).performanceLogEnabled)
        com.lin0721.linmusic.core.log.PlaybackStutterDiagnostics.initialize(this, SettingsPreferences(this).playbackStutterDiagnosticsEnabled)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = PerformanceMonitor.onActivityResumed(activity)
            override fun onActivityPaused(activity: android.app.Activity) = PerformanceMonitor.onActivityPaused(activity)
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })

        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .decoderDispatcher(Dispatchers.IO.limitedParallelism(4))
            .fetcherDispatcher(Dispatchers.IO.limitedParallelism(8))
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)
        // 在后台线程强制触发 DiskLruCache.initialize()，避免首次图片加载时锁竞争
        Thread { imageLoader.diskCache }.start()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@MelodiaApplication)
            modules(
                networkModule, repositoryModule, viewModelModule, playerModule,
                localModule, updateModule, downloadModule, localMusicModule
            )
        }

        // 初始化 WorkManager 并注入自定义 WorkerFactory
        WorkManager.initialize(
            this,
            Configuration.Builder()
                .setWorkerFactory(downloadWorkerFactory)
                .build()
        )

        // 本仓库是车机自用分支：不再做启动自动检查更新。
        // 一旦顺着官方发布包升级，本分支的横屏/副屏适配与播放页封面改动会被整体覆盖，
        // 所以这里连同更新入口一并摘掉（UpdateManager 仍在容器里，设置页入口已移除）
    }
}
