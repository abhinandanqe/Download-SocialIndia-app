package com.example.utils

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.MediaItem
import android.net.Uri

object ExoPlayerUtils {

    fun isEmulator(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT
        val model = android.os.Build.MODEL
        val manufacturer = android.os.Build.MANUFACTURER
        val brand = android.os.Build.BRAND
        val device = android.os.Build.DEVICE
        val product = android.os.Build.PRODUCT
        val hardware = android.os.Build.HARDWARE
        return (fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk", ignoreCase = true)
                || model.contains("Emulator", ignoreCase = true)
                || model.contains("Android SDK built for", ignoreCase = true)
                || manufacturer.contains("Genymotion", ignoreCase = true)
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || "google_sdk".equals(product, ignoreCase = true)
                || product.contains("sdk_gphone", ignoreCase = true)
                || hardware.contains("goldfish", ignoreCase = true)
                || hardware.contains("ranchu", ignoreCase = true))
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun getEmulatorDecoders(mimeType: String): List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
        val codecNames = when (mimeType.lowercase()) {
            "video/avc", "video/mp4v-es" -> listOf("OMX.google.h264.decoder", "c2.goldfish.h264.decoder", "c2.android.avc.decoder")
            "video/hevc" -> listOf("OMX.google.hevc.decoder", "c2.goldfish.hevc.decoder", "c2.android.hevc.decoder")
            "video/x-vnd.on2.vp9" -> listOf("OMX.google.vp9.decoder", "c2.goldfish.vp9.decoder", "c2.android.vp9.decoder")
            "video/x-vnd.on2.vp8" -> listOf("OMX.google.vp8.decoder", "c2.goldfish.vp8.decoder", "c2.android.vp8.decoder")
            "video/av01" -> listOf("OMX.google.av1.decoder", "c2.android.av1.decoder")
            "audio/mp4a-latm" -> listOf("OMX.google.aac.decoder", "c2.android.aac.decoder")
            "audio/mpeg", "audio/mp3" -> listOf("OMX.google.mp3.decoder", "c2.android.mp3.decoder")
            "audio/opus" -> listOf("OMX.google.opus.decoder", "c2.android.opus.decoder")
            "audio/flac" -> listOf("OMX.google.flac.decoder", "c2.android.flac.decoder")
            "audio/vorbis" -> listOf("OMX.google.vorbis.decoder", "c2.android.vorbis.decoder")
            else -> emptyList()
        }
        if (codecNames.isEmpty()) return emptyList()

        return codecNames.mapNotNull { name ->
            try {
                androidx.media3.exoplayer.mediacodec.MediaCodecInfo.newInstance(
                    name,
                    mimeType,
                    mimeType,
                    null,
                    false,
                    true,
                    false,
                    false,
                    false
                )
            } catch (_: Throwable) {
                null
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun createExoPlayer(context: Context, isMusic: Boolean = false): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(if (isMusic) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        // Hardware acceleration: Use MediaCodecSelector.DEFAULT on physical mobile devices so Qualcomm, MediaTek,
        // and Exynos hardware video decoders run at full 60fps with zero lag.
        // Only on emulators/virtual environments do we prioritize software/goldfish decoders to prevent Codec2 crash.
        val safeCodecSelector = if (isEmulator()) {
            MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val systemDecoders = try {
                    MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                } catch (_: Throwable) {
                    emptyList()
                }

                if (systemDecoders.isNotEmpty()) {
                    systemDecoders.sortedWith(
                        compareByDescending<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
                            it.name.startsWith("c2.android", ignoreCase = true) || it.name.startsWith("OMX.google", ignoreCase = true)
                        }.thenByDescending {
                            it.softwareOnly
                        }.thenByDescending {
                            it.name.contains("goldfish", ignoreCase = true)
                        }.thenByDescending {
                            it.name.startsWith("OMX", ignoreCase = true)
                        }
                    )
                } else {
                    getEmulatorDecoders(mimeType)
                }
            }
        } else {
            MediaCodecSelector.DEFAULT
        }

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                if (!isMusic) {
                    super.buildVideoRenderers(
                        context,
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
                        safeCodecSelector,
                        true, // enableDecoderFallback
                        eventHandler,
                        eventListener,
                        allowedVideoJoiningTimeMs,
                        out
                    )
                }
            }

            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: androidx.media3.exoplayer.audio.AudioSink,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                super.buildAudioRenderers(
                    context,
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
                    safeCodecSelector,
                    true, // enableDecoderFallback
                    audioSink,
                    eventHandler,
                    eventListener,
                    out
                )
            }
        }.apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }

        // Smart DataSource Factory: Local files & content URIs (camera recordings, gallery video preview)
        // bypass CacheDataSource to prevent SimpleCache locking/errors and guarantee instant playback.
        val baseHttpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        val defaultLocalFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, baseHttpFactory)
        val remoteCacheFactory = VideoCacheManager.createCacheDataSourceFactory(context)
        val smartDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            object : androidx.media3.datasource.DataSource {
                private var activeSource: androidx.media3.datasource.DataSource? = null
                private val transferListeners = mutableListOf<androidx.media3.datasource.TransferListener>()

                override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                    if (!transferListeners.contains(transferListener)) {
                        transferListeners.add(transferListener)
                    }
                    activeSource?.addTransferListener(transferListener)
                }

                override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                    val scheme = dataSpec.uri.scheme?.lowercase()
                    val src = if (scheme == "http" || scheme == "https") {
                        remoteCacheFactory.createDataSource()
                    } else {
                        defaultLocalFactory.createDataSource()
                    }
                    transferListeners.forEach { src.addTransferListener(it) }
                    activeSource = src
                    return src.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return activeSource?.read(buffer, offset, length) ?: -1
                }

                override fun getUri(): android.net.Uri? {
                    return activeSource?.uri
                }

                override fun getResponseHeaders(): Map<String, List<String>> {
                    return activeSource?.responseHeaders ?: emptyMap()
                }

                override fun close() {
                    try {
                        activeSource?.close()
                    } finally {
                        activeSource = null
                    }
                }
            }
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(smartDataSourceFactory)

        val lowRamDevice = VideoCacheManager.isLowRam(context) || NetworkMonitor.effectiveLiteMode.value
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (lowRamDevice) 2000 else 3000, // minBufferMs (lightweight RAM footprint)
                if (lowRamDevice) 6000 else 10000, // maxBufferMs
                250,   // bufferForPlaybackMs (instant 250ms start)
                500    // bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(if (lowRamDevice) 2 * 1024 * 1024 else 4 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, false)
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 1.0f
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .clearVideoSizeConstraints()
                    .setMaxVideoBitrate(if (lowRamDevice) 2_500_000 else Int.MAX_VALUE)
                    .build()
            }
            
        addRecoveryListener(player)
        return player
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun createMediaSource(context: Context, url: String): androidx.media3.exoplayer.source.MediaSource {
        val dataSourceFactory = VideoCacheManager.createCacheDataSourceFactory(context)
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
    }

    fun createTexturePlayerView(context: Context, initialResizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT): androidx.media3.ui.PlayerView {
        val playerView = try {
            val view = android.view.LayoutInflater.from(context).inflate(com.example.R.layout.texture_player_view, null, false) as androidx.media3.ui.PlayerView
            view.apply {
                useController = false
                resizeMode = initialResizeMode
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        } catch (e: Exception) {
            androidx.media3.ui.PlayerView(context).apply {
                useController = false
                resizeMode = initialResizeMode
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
        return playerView.apply {
            clipChildren = true
            clipToPadding = true
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BOUNDS
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                importantForContentCapture = android.view.View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
            }
            try {
                val contentFrame = findViewById<android.view.ViewGroup>(androidx.media3.ui.R.id.exo_content_frame)
                contentFrame?.apply {
                    clipChildren = true
                    clipToPadding = true
                    clipToOutline = true
                    outlineProvider = android.view.ViewOutlineProvider.BOUNDS
                }
            } catch (_: Throwable) {}
        }
    }

    // Warm ExoPlayer pool (max 2 instances) to eliminate main-thread MediaCodec creation/release freezes during scrolling
    private const val MAX_POOL_SIZE = 2
    private val recycledPlayerPool = java.util.concurrent.ConcurrentLinkedQueue<ExoPlayer>()
    private val boundItemListeners = java.util.concurrent.ConcurrentHashMap<ExoPlayer, Player.Listener>()

    fun bindItemListener(player: ExoPlayer, listener: Player.Listener) {
        boundItemListeners.remove(player)?.let { old ->
            try { player.removeListener(old) } catch (_: Throwable) {}
        }
        boundItemListeners[player] = listener
        player.addListener(listener)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun acquireRecycledPlayer(context: Context, isMusic: Boolean = false): ExoPlayer {
        if (!isMusic) {
            while (true) {
                val pooled = recycledPlayerPool.poll() ?: break
                try {
                    boundItemListeners.remove(pooled)?.let { pooled.removeListener(it) }
                    pooled.stop()
                    pooled.clearMediaItems()
                    pooled.clearVideoSurface()
                    pooled.volume = 1.0f
                    pooled.setPlaybackSpeed(1.0f)
                    applyLiteModeConstraints(pooled)
                    return pooled
                } catch (_: Throwable) {
                    safeRelease(pooled)
                }
            }
        }
        return createExoPlayer(context.applicationContext, isMusic).also {
            it.volume = 1.0f
            if (!isMusic) applyLiteModeConstraints(it)
        }
    }

    fun releaseToPool(player: ExoPlayer?) {
        if (player == null) return
        try {
            boundItemListeners.remove(player)?.let { player.removeListener(it) }
            player.playWhenReady = false
            player.volume = 0f
            player.stop()
            player.clearMediaItems()
            player.clearVideoSurface()
            if (recycledPlayerPool.size < MAX_POOL_SIZE) {
                recycledPlayerPool.offer(player)
                return
            }
        } catch (_: Throwable) {}
        safeRelease(player)
    }

    fun evictIdlePooledPlayers() {
        while (recycledPlayerPool.isNotEmpty()) {
            val p = recycledPlayerPool.poll()
            if (p != null) {
                boundItemListeners.remove(p)
                safeRelease(p)
            }
        }
    }

    fun applyLiteModeConstraints(player: ExoPlayer) {
        try {
            val lowRam = NetworkMonitor.effectiveLiteMode.value
            val paramsBuilder = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .clearVideoSizeConstraints()
                .setMaxVideoBitrate(if (lowRam) 2_500_000 else Int.MAX_VALUE)
            player.trackSelectionParameters = paramsBuilder.build()
        } catch (_: Throwable) {}
    }

    /**
     * Fast poster & image request builder that avoids SubcomposeLayout and never attaches
     * VideoFrameDecoder over HTTP/HTTPS (which would stream megabytes of video on scroll).
     */
    fun buildFastPosterRequest(
        context: Context,
        url: Any?,
        widthPx: Int = 720,
        heightPx: Int = 960,
        onError: (() -> Unit)? = null
    ): coil.request.ImageRequest {
        val lowRam = VideoCacheManager.isLowRam(context) || NetworkMonitor.effectiveLiteMode.value
        val targetW = (if (lowRam) (widthPx * 0.7f).toInt() else widthPx).coerceIn(120, 1080)
        val targetH = (if (lowRam) (heightPx * 0.7f).toInt() else heightPx).coerceIn(120, 1440)
        val urlStr = (url as? String).orEmpty()
        val isLocalUri = urlStr.startsWith("file://") || urlStr.startsWith("content://")
        val builder = coil.request.ImageRequest.Builder(context)
            .data(urlStr.takeIf { it.isNotBlank() } ?: url)
            .size(targetW, targetH)
            .precision(coil.size.Precision.INEXACT)
            .allowRgb565(true)
            .allowHardware(true)
            .crossfade(false)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        if (isLocalUri) {
            builder.decoderFactory(coil.decode.VideoFrameDecoder.Factory())
        }
        if (onError != null) {
            builder.listener(onError = { _, _ -> onError() })
        }
        return builder.build()
    }

    /**
     * RAM & Bitmap Guard: Downsamples heavy images to match exact viewport dimensions
     * and uses RGB_565 on opaque bitmaps to prevent OutOfMemoryError and speed up scrolling.
     */
    fun buildViewportImageRequest(
        context: Context,
        url: Any?,
        widthPx: Int = 1080,
        heightPx: Int = 1350
    ): coil.request.ImageRequest {
        val lowRam = VideoCacheManager.isLowRam(context) || NetworkMonitor.effectiveLiteMode.value
        val targetW = (if (lowRam) (widthPx * 0.75f).toInt() else widthPx).coerceIn(120, 1440)
        val targetH = (if (lowRam) (heightPx * 0.75f).toInt() else heightPx).coerceIn(120, 1920)
        val safeData = when (url) {
            is Int -> if ((url ushr 24) == 0x7f || (url ushr 24) == 0x01) url else null
            is String -> url.takeIf { it.isNotBlank() }
            else -> url
        }
        return coil.request.ImageRequest.Builder(context)
            .data(safeData)
            .size(targetW, targetH)
            .precision(coil.size.Precision.INEXACT)
            .allowRgb565(true)
            .allowHardware(true)
            .crossfade(if (lowRam) 50 else 80)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .build()
    }

    fun safeRelease(player: Player?) {
        try {
            if (player is ExoPlayer) {
                boundItemListeners.remove(player)?.let { player.removeListener(it) }
            }
            player?.stop()
            player?.clearMediaItems()
            player?.release()
        } catch (e: Throwable) {
            // Ignore release errors during shutdown
        }
    }

    fun addRecoveryListener(player: ExoPlayer) {
        var retryCount = 0
        val maxRetries = 1
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.w("ExoPlayerError", "Error playing media: ${error.message}")
                if (retryCount < maxRetries && (
                    error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED || 
                    error.message?.contains("AudioSink") == true ||
                    error.message?.contains("discontinuity") == true
                )) {
                    retryCount++
                    if (player.playbackState == Player.STATE_IDLE) {
                        try {
                            player.prepare()
                        } catch (_: Exception) {}
                    }
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    retryCount = 0
                }
            }
        })
    }
}
