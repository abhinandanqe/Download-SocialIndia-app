package com.example.ui

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun MiniAutoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isImage = com.example.utils.MediaResolver.isImage(videoUrl)

    if (isImage) {
        coil.compose.AsyncImage(
            model = videoUrl,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    val exoPlayer = remember {
        com.example.utils.ExoPlayerUtils.createExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            val playUrl = com.example.utils.MediaResolver.resolveVideoUrl(videoUrl)
            val mediaItem = MediaItem.Builder().setUri(Uri.parse(playUrl)).build()
            setMediaItem(mediaItem)
            prepare()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> exoPlayer.playWhenReady = true
                Lifecycle.Event.ON_PAUSE -> exoPlayer.playWhenReady = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            com.example.utils.ExoPlayerUtils.safeRelease(exoPlayer)
        }
    }

    AndroidView(
        factory = { ctx ->
            com.example.utils.ExoPlayerUtils.createTexturePlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        update = { view ->
            if (view.player != exoPlayer) {
                view.player = exoPlayer
            }
        },
        modifier = modifier
    )
}
