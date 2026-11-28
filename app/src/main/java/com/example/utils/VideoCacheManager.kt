package com.example.utils

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
object VideoCacheManager {
    private var simpleCache: SimpleCache? = null
    private const val CACHE_SIZE = 500 * 1024 * 1024L // 500MB high-speed video cache

    /**
     * Generates a valid, playable 1-second 8kHz mono PCM WAV byte array as a last-resort offline fallback
     * so OkHttpDataSource never throws InvalidResponseCodeException (403/404).
     */
    private val fallbackSilentWavBytes: ByteArray by lazy {
        val sampleRate = 8000
        val numSamples = sampleRate // 1 second
        val dataSize = numSamples * 2 // 16-bit mono
        val totalSize = 36 + dataSize
        val buffer = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // PCM chunk size
        buffer.putShort(1) // AudioFormat = 1 (PCM)
        buffer.putShort(1) // NumChannels = 1
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // ByteRate
        buffer.putShort(2) // BlockAlign
        buffer.putShort(16) // BitsPerSample
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        buffer.array()
    }

    private fun buildSyntheticWavResponse(request: okhttp3.Request): okhttp3.Response {
        val bodyBytes = fallbackSilentWavBytes
        val body = bodyBytes.toResponseBody("audio/wav".toMediaTypeOrNull())
        val rangeHeader = request.header("Range")
        val startOffset = rangeHeader
            ?.removePrefix("bytes=")
            ?.substringBefore("-")
            ?.toLongOrNull() ?: 0L

        return if (startOffset > 0L) {
            val endOffset = startOffset + bodyBytes.size - 1
            val totalLen = endOffset + 1
            okhttp3.Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(206)
                .message("Partial Content")
                .header("Content-Type", "audio/wav")
                .header("Content-Range", "bytes $startOffset-$endOffset/$totalLen")
                .header("Content-Length", bodyBytes.size.toString())
                .header("Accept-Ranges", "bytes")
                .body(body)
                .build()
        } else {
            okhttp3.Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "audio/wav")
                .header("Content-Length", bodyBytes.size.toString())
                .header("Accept-Ranges", "bytes")
                .body(body)
                .build()
        }
    }

    private fun extractDriveUuidFromHtml(html: String): String? {
        val inputRegex = Regex("""name=["']uuid["']\s+value=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        inputRegex.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return it }
        val paramRegex = Regex("""[?&]uuid=([a-zA-Z0-9_-]+)""")
        return paramRegex.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val originalReq = chain.request()
            val rawUrl = originalReq.url.toString()
            val cleanedRawUrl = MediaResolver.stripExtParam(rawUrl)
            val driveFileId = MediaResolver.extractGoogleDriveFileId(cleanedRawUrl)
            val sanitizedUrl = if (!driveFileId.isNullOrEmpty() && !cleanedRawUrl.contains("uuid=")) {
                "https://drive.usercontent.google.com/download?id=$driveFileId&export=download&confirm=t"
            } else {
                MediaResolver.sanitizeKnownBrokenUrl(cleanedRawUrl)
            }

            if (sanitizedUrl.isBlank()) {
                if (MediaResolver.isAudioUrl(rawUrl)) {
                    return@addInterceptor buildSyntheticWavResponse(originalReq)
                }
                throw java.io.IOException("Empty media URL")
            }

            val activeReq = if (sanitizedUrl != rawUrl) {
                originalReq.newBuilder()
                    .url(sanitizedUrl)
                    .build()
            } else {
                originalReq
            }

            try {
                var response = chain.proceed(activeReq)
                var isHtmlResponse = !driveFileId.isNullOrEmpty() &&
                        response.header("Content-Type")?.contains("text/html", ignoreCase = true) == true

                // If server rejected the Range header with 400 or 416, retry cleanly without Range
                if ((response.code == 400 || response.code == 416) && activeReq.header("Range") != null) {
                    response.close()
                    response = chain.proceed(activeReq.newBuilder().removeHeader("Range").build())
                    isHtmlResponse = !driveFileId.isNullOrEmpty() &&
                            response.header("Content-Type")?.contains("text/html", ignoreCase = true) == true
                }

                if ((response.isSuccessful || response.code == 206) && !isHtmlResponse) {
                    return@addInterceptor response
                }

                // If this is a Google Drive file and the request failed (e.g. 400/403/404) or returned an HTML virus-scan warning page,
                // cascade through confirmed download and OAuth Drive API streaming fallbacks.
                if (!driveFileId.isNullOrEmpty()) {
                    GoogleDrivePermissionRepairManager.repairPermissionAsync(driveFileId)
                    val candidates = mutableListOf<okhttp3.Request>()

                    if (isHtmlResponse) {
                        val htmlBody = try { response.peekBody(65536).string() } catch (_: Exception) { "" }
                        val uuid = extractDriveUuidFromHtml(htmlBody)
                        if (!uuid.isNullOrBlank()) {
                            val confirmedUuidUrl = "https://drive.usercontent.google.com/download?id=$driveFileId&export=download&confirm=t&uuid=$uuid"
                            candidates.add(originalReq.newBuilder().url(confirmedUuidUrl).build())
                        }
                    }

                    val bearerToken = GoogleDrivePermissionRepairManager.getValidBearerToken()
                    if (!bearerToken.isNullOrBlank() && !bearerToken.startsWith("1//")) {
                        candidates.add(
                            originalReq.newBuilder()
                                .url("https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media")
                                .header("Authorization", "Bearer $bearerToken")
                                .build()
                        )
                    }

                    val directUserContentUrl = "https://drive.usercontent.google.com/download?id=$driveFileId&export=download&confirm=t"
                    if (activeReq.url.toString() != directUserContentUrl) {
                        candidates.add(originalReq.newBuilder().url(directUserContentUrl).build())
                    }

                    val ucDownloadUrl = "https://drive.google.com/uc?export=download&id=$driveFileId&confirm=t"
                    if (activeReq.url.toString() != ucDownloadUrl) {
                        candidates.add(originalReq.newBuilder().url(ucDownloadUrl).build())
                    }

                    for (candidateReq in candidates) {
                        try {
                            response.close()
                            var candidateResp = chain.proceed(candidateReq)
                            if ((candidateResp.code == 400 || candidateResp.code == 416) && candidateReq.header("Range") != null) {
                                candidateResp.close()
                                candidateResp = chain.proceed(candidateReq.newBuilder().removeHeader("Range").build())
                            }
                            val candidateIsHtml = candidateResp.header("Content-Type")?.contains("text/html", ignoreCase = true) == true
                            if ((candidateResp.isSuccessful || candidateResp.code == 206) && !candidateIsHtml) {
                                return@addInterceptor candidateResp
                            }
                            if (candidateIsHtml) {
                                val htmlBody = try { candidateResp.peekBody(65536).string() } catch (_: Exception) { "" }
                                val uuid = extractDriveUuidFromHtml(htmlBody)
                                if (!uuid.isNullOrBlank()) {
                                    candidateResp.close()
                                    val uuidUrl = "https://drive.usercontent.google.com/download?id=$driveFileId&export=download&confirm=t&uuid=$uuid"
                                    val uuidResp = chain.proceed(originalReq.newBuilder().url(uuidUrl).build())
                                    val uuidIsHtml = uuidResp.header("Content-Type")?.contains("text/html", ignoreCase = true) == true
                                    if ((uuidResp.isSuccessful || uuidResp.code == 206) && !uuidIsHtml) {
                                        return@addInterceptor uuidResp
                                    }
                                    response = uuidResp
                                    continue
                                }
                            }
                            response = candidateResp
                        } catch (_: Exception) {
                            // Try next candidate
                        }
                    }
                }

                response.close()
                MediaResolver.markDeadUrl(rawUrl)
                MediaResolver.markDeadUrl(cleanedRawUrl)
                if (MediaResolver.isAudioUrl(rawUrl)) {
                    val fallbackUrl = MediaResolver.FALLBACK_AUDIO_URL
                    val rangeHeader = originalReq.header("Range")
                    val fallbackReqBuilder = originalReq.newBuilder().url(fallbackUrl)
                    if (rangeHeader != null && !rangeHeader.startsWith("bytes=0-")) {
                        fallbackReqBuilder.removeHeader("Range")
                    }
                    val fallbackResp = try { chain.proceed(fallbackReqBuilder.build()) } catch (_: Exception) { null }
                    if (fallbackResp != null && (fallbackResp.isSuccessful || fallbackResp.code == 206)) {
                        return@addInterceptor fallbackResp
                    } else {
                        fallbackResp?.close()
                        return@addInterceptor buildSyntheticWavResponse(originalReq)
                    }
                } else {
                    throw java.io.IOException("Video stream HTTP ${response.code} for $sanitizedUrl")
                }
            } catch (e: Exception) {
                if (e is java.io.IOException) {
                    throw e
                }
                MediaResolver.markDeadUrl(rawUrl)
                MediaResolver.markDeadUrl(cleanedRawUrl)
                if (MediaResolver.isAudioUrl(rawUrl)) {
                    buildSyntheticWavResponse(originalReq)
                } else {
                    throw java.io.IOException("Video stream error: ${e.message}", e)
                }
            }
        }
        .cookieJar(object : okhttp3.CookieJar {
            private val cookieStore = java.util.concurrent.CopyOnWriteArrayList<okhttp3.Cookie>()
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                cookieStore.addAll(cookies)
            }
            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                val validCookies = mutableListOf<okhttp3.Cookie>()
                val it = cookieStore.iterator()
                while (it.hasNext()) {
                    val cookie = it.next()
                    if (cookie.expiresAt < System.currentTimeMillis()) {
                        cookieStore.remove(cookie)
                    } else if (cookie.matches(url)) {
                        validCookies.add(cookie)
                    }
                }
                return validCookies
            }
        })
        .build()

    @Synchronized
    fun init(context: Context) {
        if (simpleCache == null) {
            try {
                // Purge legacy cache directories that may contain poisoned entries
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        File(context.cacheDir, "video_cache").deleteRecursively()
                        File(context.cacheDir, "video_cache_v2").deleteRecursively()
                        File(context.cacheDir, "video_cache_v3").deleteRecursively()
                    } catch (_: Exception) {}
                }
                val cacheDir = File(context.cacheDir, "video_cache_v4")
                val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
                val databaseProvider = StandaloneDatabaseProvider(context)
                simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
            } catch (e: Exception) {
                android.util.Log.e("VideoCacheManager", "Failed to initialize cache", e)
            }
        }
    }

    fun getCache(context: Context): SimpleCache? {
        init(context)
        return simpleCache
    }

    fun createCacheDataSourceFactory(context: Context): androidx.media3.datasource.DataSource.Factory {
        init(context)
        val upstreamFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, upstreamFactory)
        
        val cache = simpleCache
        return if (cache != null) {
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(defaultDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            defaultDataSourceFactory
        }
    }

    fun buildCachedMediaSource(context: Context, url: String): androidx.media3.exoplayer.source.MediaSource {
        val resolved = MediaResolver.resolveVideoUrl(url).ifBlank { url }
        val dataSourceFactory = createCacheDataSourceFactory(context)
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(Uri.parse(resolved))
            .build()
        return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    private val activePrefetches = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    fun isWifi(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            false
        }
    }

    fun isLowRam(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
            am.isLowRamDevice
        } catch (e: Exception) {
            false
        }
    }

    fun isCached(url: String): Boolean {
        val cache = simpleCache ?: return false
        if (url.isEmpty()) return false
        return try {
            val uri = Uri.parse(url)
            val key = androidx.media3.datasource.cache.CacheKeyFactory.DEFAULT.buildCacheKey(androidx.media3.datasource.DataSpec(uri))
            cache.getCachedBytes(key, 0, 256 * 1024) > 0
        } catch (e: Exception) {
            false
        }
    }

    fun cancelPrefetches() {
        activePrefetches.values.forEach { it.cancel() }
        activePrefetches.clear()
    }

    // Pre-fetch next 2 Reels/Feed videos in parallel background coroutines for instant swipe playback
    fun prefetchNextVideos(context: Context, urls: List<String>, maxCount: Int = 2) {
        val validUrls = urls
            .map { MediaResolver.resolveVideoUrl(it) }
            .filter { it.isNotBlank() && !MediaResolver.isImage(it) }
            .distinct()
            .take(maxCount.coerceAtMost(2))
        validUrls.forEach { url ->
            prefetchVideo(context, url)
        }
    }

    // Pre-fetch next video streams adaptively in the background
    fun prefetchVideo(context: Context, url: String) {
        val resolvedUrl = MediaResolver.resolveVideoUrl(url)
        if (resolvedUrl.isEmpty()) return
        
        // Skip pre-fetching if it's already an image or local asset
        if (resolvedUrl.contains("type=image") || resolvedUrl.contains("/image/") || resolvedUrl.endsWith(".jpg") || resolvedUrl.endsWith(".jpeg") || resolvedUrl.endsWith(".png") || resolvedUrl.endsWith(".webp")) {
            return
        }

        init(context)
        val cache = simpleCache ?: return

        // Skip if already in cache or currently prefetching
        if (isCached(resolvedUrl) || activePrefetches.containsKey(resolvedUrl)) {
            return
        }

        val wifi = isWifi(context)
        val lowRam = isLowRam(context)
        val liteMode = NetworkMonitor.effectiveLiteMode.value
        
        // Fast prefetch chunk size (2MB on WiFi, 1MB on standard mobile data, 384KB in Lite Mode / Low RAM)
        val chunkSize: Long = when {
            liteMode || lowRam -> 384 * 1024L
            wifi -> 2048 * 1024L
            else -> 1024 * 1024L
        }

        // Keep up to 2 concurrent prefetch jobs active for next-2 videos pre-caching
        val maxConcurrent = 2
        if (activePrefetches.size >= maxConcurrent) {
            val oldestKey = activePrefetches.keys().toList().firstOrNull()
            if (oldestKey != null) {
                activePrefetches.remove(oldestKey)?.cancel()
            }
        }

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val uri = Uri.parse(resolvedUrl)
                val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)
                    .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                val dataSource = DefaultDataSource.Factory(context, upstreamFactory).createDataSource()
                val dataSpec = androidx.media3.datasource.DataSpec(uri, 0, chunkSize)
                
                val cacheWriter = androidx.media3.datasource.cache.CacheWriter(
                    CacheDataSource(cache, dataSource),
                    dataSpec,
                    null,
                    null
                )
                cacheWriter.cache()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected when user scrolls past or backgrounded
            } catch (e: Exception) {
                android.util.Log.w("VideoCacheManager", "Prefetch aborted for: $resolvedUrl - ${e.message}")
            } finally {
                activePrefetches.remove(resolvedUrl)
            }
        }
        activePrefetches[resolvedUrl] = job
    }
}
