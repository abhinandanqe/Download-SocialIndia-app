package com.example
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

class MyApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        myAppInstance = this

        if (com.example.crash.CrashHandler.isCrashProcess(this)) {
            return
        }
        
        try {
            androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_ERROR)
        } catch (e: Exception) {}

        // Safe crash guard to log and safely suppress non-fatal background crashes without freezing the main looper
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("MyApplication", "Safe guard caught uncaught exception on thread: ${thread.name}", throwable)
            val isMainThread = android.os.Looper.getMainLooper().thread == thread
            val msg = throwable.message ?: ""
            val isRecoverable = msg.contains("Codec", ignoreCase = true) ||
                    msg.contains("Media", ignoreCase = true) ||
                    msg.contains("Zego", ignoreCase = true) ||
                    msg.contains("Surface", ignoreCase = true) ||
                    msg.contains("OpenGL", ignoreCase = true) ||
                    msg.contains("IndexOutOfBounds", ignoreCase = true) ||
                    msg.contains("NullPointerException", ignoreCase = true) ||
                    throwable is IllegalStateException ||
                    throwable.stackTrace.any { 
                        it.className.contains("media", ignoreCase = true) || 
                        it.className.contains("zego", ignoreCase = true) ||
                        it.className.contains("exo", ignoreCase = true) ||
                        it.className.contains("compose", ignoreCase = true)
                    }

            if (!isMainThread && isRecoverable) {
                // Ignore recoverable background thread exceptions so app remains stable
                return@setDefaultUncaughtExceptionHandler
            }
            if (!com.example.crash.CrashHandler.launchCrashScreen(this, thread, throwable)) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        
        try {
            com.example.utils.ThemePreference.init(this)
            com.example.utils.ReelsPreferencesManager.init(this)
            com.example.utils.SoundManager.init(this)
            com.example.utils.NetworkMonitor.init(this)
        } catch (e: Exception) {
            android.util.Log.e("MyApplication", "Utils init error", e)
        }
        
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("MyApplication", "Firebase init error", e)
        }
        
        try {
            com.example.model.RepositoryManager.init(this)
            com.example.notification.SocialIndiaNotificationManager.setupNotificationChannels(this)
        } catch (e: Exception) {
            android.util.Log.e("MyApplication", "Core init error", e)
        }

        // Fast startup path: initialize video cache immediately, warm Drive tokens async,
        // and stagger heavy secondary SDKs so initial Home Feed loading gets 100% bandwidth & CPU.
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            val appCtx = applicationContext
            try {
                com.example.utils.VideoCacheManager.init(appCtx)
            } catch (e: Exception) {
                android.util.Log.e("MyApplication", "VideoCacheManager async init error", e)
            }
            try {
                com.example.auth.MultiAccountManager.init(appCtx)
            } catch (e: Exception) {
                android.util.Log.e("MyApplication", "MultiAccountManager async init error", e)
            }
            try {
                com.example.utils.GoogleDrivePermissionRepairManager.warmBearerTokensAsync()
            } catch (_: Exception) {}

            // Stagger secondary services so first feed render and initial posts load at peak speed
            kotlinx.coroutines.delay(900)
            try {
                com.example.security.FaceLockManager.init(appCtx)
            } catch (e: Exception) {
                android.util.Log.e("MyApplication", "FaceLockManager async init error", e)
            }
            try {
                com.example.zego.SocialIndiaZegoConfig.startObserving()
                com.example.model.LivePresenceManager.init()
            } catch (e: Exception) {
                android.util.Log.e("MyApplication", "LivePresenceManager async init error", e)
            }
            try {
                val currentUid = com.example.model.RepositoryManager.getCurrentUserId()
                if (currentUid.isNotEmpty()) {
                    com.example.model.DatabaseInitializer.initializeUserSchemaSafe(currentUid)
                }
            } catch (e: Exception) {
                android.util.Log.e("MyApplication", "DatabaseInitializer async init error", e)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val isLowRam = com.example.utils.VideoCacheManager.isLowRam(this)
        val coilClient = okhttp3.OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(8, 5, java.util.concurrent.TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(coilClient)
            .components {
                add(coil.map.Mapper<String, String> { data, _ ->
                    if (data.contains("drive.google.com") ||
                        data.contains("googleapis.com/drive") ||
                        data.contains("drive.usercontent.google.com")
                    ) {
                        com.example.utils.MediaResolver.resolveImageUrl(data).ifEmpty { null }
                    } else {
                        null
                    }
                })
                add(coil.map.Mapper<Int, android.net.Uri> { data, _ ->
                    val pkg = data ushr 24
                    if (pkg == 0x7f || pkg == 0x01) null else android.net.Uri.EMPTY
                })
                // Only decode video frames for local file/content URIs (never stream remote HTTP videos via MediaMetadataRetriever)
                add(coil.decode.Decoder.Factory { result, options, imageLoader ->
                    val mime = result.mimeType.orEmpty()
                    val isLocalSource = result.source.fileOrNull() != null &&
                            result.dataSource != coil.decode.DataSource.NETWORK
                    if (isLocalSource && mime.startsWith("video/", ignoreCase = true)) {
                        VideoFrameDecoder.Factory().create(result, options, imageLoader)
                    } else {
                        null
                    }
                })
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (isLowRam) 0.20 else 0.30) // Adaptive heap guard for 3GB/4GB RAM phones
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(350L * 1024 * 1024) // Fast 350MB disk cache
                    .build()
            }
            .crossfade(if (isLowRam) 50 else 80)
            .allowRgb565(true) // Safely uses RGB_565 only for opaque images (halves RAM & speeds GPU upload)
            .allowHardware(true) // Full GPU hardware acceleration for bitmaps
            .respectCacheHeaders(false) // Aggressively cache social media avatars & post media
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) {
            try {
                coil.Coil.imageLoader(this).memoryCache?.clear()
                com.example.utils.ExoPlayerUtils.evictIdlePooledPlayers()
            } catch (_: Exception) {}
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            coil.Coil.imageLoader(this).memoryCache?.clear()
            com.example.utils.ExoPlayerUtils.evictIdlePooledPlayers()
        } catch (_: Exception) {}
    }
}

lateinit var myAppInstance: MyApplication
