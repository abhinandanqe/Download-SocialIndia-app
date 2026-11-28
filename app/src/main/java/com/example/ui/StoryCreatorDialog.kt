package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.Calendar
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.compose.rememberAsyncImagePainter
import com.example.api.ITunesTrack
import com.example.model.RepositoryManager
import com.example.model.StorySticker
import com.example.utils.ExoPlayerUtils
import com.example.utils.SoundManager
import com.example.viewmodel.StoryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(UnstableApi::class)
@Composable
fun StoryCreatorDialog(
    onDismiss: () -> Unit,
    initialText: String = "",
    mentionedUserId: String? = null,
    mentionedUsername: String? = null,
    isReback: Boolean = false,
    initialMediaUri: Uri? = null,
    repostVideoUrl: String? = null,
    repostedPostId: String? = null,
    storyViewModel: StoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val uploadProgress by storyViewModel.uploadProgress.collectAsStateWithLifecycle()
    var isPublishingStory by remember { mutableStateOf(false) }
    var publishErrorMessage by remember { mutableStateOf<String?>(null) }

    // Media State
    val initialUri = initialMediaUri ?: repostVideoUrl?.let { Uri.parse(it) }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(initialUri) }
    var isVideo by remember { mutableStateOf(repostVideoUrl != null || (initialUri != null && initialUri.toString().contains("video"))) }

    // Canvas Background Gradient (when no media)
    val gradientPresets = remember {
        listOf(
            Brush.verticalGradient(listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCAF45))), // Sunset
            Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))), // Midnight
            Brush.verticalGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D))), // Neon Emerald
            Brush.verticalGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))), // Coral
            Brush.verticalGradient(listOf(Color(0xFF654EA3), Color(0xFFEAAFC8))), // Lavender Dream
            Brush.verticalGradient(listOf(Color(0xFF1F1C2C), Color(0xFF928DAB)))  // Dark Violet
        )
    }
    var activeGradientIndex by remember { mutableIntStateOf(0) }

    // Filter preset state
    var activeFilterIndex by remember { mutableIntStateOf(0) }
    var showFiltersCarousel by remember { mutableStateOf(false) }

    // Drawing State
    var isDrawingMode by remember { mutableStateOf(false) }
    var drawingStrokes by remember { mutableStateOf<List<DrawnStroke>>(emptyList()) }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var activeBrushColor by remember { mutableStateOf(Color.White) }
    var activeBrushWidth by remember { mutableFloatStateOf(14f) }

    // Text Elements State
    var textElements by remember {
        mutableStateOf<List<StoryTextElement>>(
            if (initialText.isNotEmpty()) listOf(StoryTextElement(text = initialText)) else emptyList()
        )
    }
    var isEditingText by remember { mutableStateOf(false) }
    var editingTextItem by remember { mutableStateOf<StoryTextElement?>(null) }

    // Sticker Elements State
    var stickerElements by remember { mutableStateOf<List<StoryStickerElement>>(emptyList()) }
    var isGradientStoryMode by remember { mutableStateOf(false) }

    val hasCreatedContent = selectedMediaUri != null || isGradientStoryMode || textElements.isNotEmpty() || stickerElements.isNotEmpty() || drawingStrokes.isNotEmpty()

    // Sheets and Builder Modals
    var showStickerSheet by remember { mutableStateOf(false) }
    var showMusicPicker by remember { mutableStateOf(false) }
    var showPollBuilder by remember { mutableStateOf(false) }
    var showQuestionBuilder by remember { mutableStateOf(false) }
    var showQuizBuilder by remember { mutableStateOf(false) }
    var showSliderBuilder by remember { mutableStateOf(false) }
    var showAddYoursBuilder by remember { mutableStateOf(false) }
    var showMentionBuilder by remember { mutableStateOf(false) }
    var showLocationBuilder by remember { mutableStateOf(false) }
    var showHashtagBuilder by remember { mutableStateOf(false) }

    // Exclusive Story Pay-to-Unlock State (Verified Creators Only)
    val currentUser by com.example.model.RepositoryManager.currentUserState.collectAsStateWithLifecycle()
    val isVerifiedCreator = currentUser?.let { u ->
        !u.verificationSuspended && (
            u.isVerified ||
            u.verificationStatus == "approved" ||
            u.verificationStatus == com.example.model.VerificationStatus.VERIFIED_ACTIVE
        )
    } == true
    var isExclusiveStory by remember { mutableStateOf(false) }
    var exclusiveStoryPriceText by remember { mutableStateOf("29") }
    var exclusiveStoryTeaser by remember { mutableStateOf("") }
    var showExclusiveStoryConfig by remember { mutableStateOf(false) }
    val monetizationSettings by com.example.repository.MonetizationRepository.observeMonetizationSettings()
        .collectAsStateWithLifecycle(initialValue = com.example.model.MonetizationSettings())

    // Music State
    var selectedMusicTrack by remember { mutableStateOf<ITunesTrack?>(null) }
    var isMusicPlaying by remember { mutableStateOf(false) }
    val musicAudioPlayer = remember {
        try {
            ExoPlayerUtils.createExoPlayer(context, isMusic = true).apply {
                repeatMode = Player.REPEAT_MODE_ALL
            }
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(musicAudioPlayer) {
        onDispose {
            ExoPlayerUtils.safeRelease(musicAudioPlayer)
        }
    }

    LaunchedEffect(selectedMusicTrack, isMusicPlaying) {
        val url = selectedMusicTrack?.previewUrl
        if (!url.isNullOrEmpty() && isMusicPlaying) {
            try {
                musicAudioPlayer?.setMediaItem(MediaItem.fromUri(url))
                musicAudioPlayer?.prepare()
                musicAudioPlayer?.play()
            } catch (e: Exception) {
                // Ignore audio playback error
            }
        } else {
            musicAudioPlayer?.pause()
        }
    }

    // Camera Settings
    var isFrontCamera by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(2) } // 0 = Off, 1 = On, 2 = Auto (Intelligent Night & Flash), 3 = Torch (Back Flash), 4 = Night Screen Glow
    var timerSetting by remember { mutableIntStateOf(0) } // 0 = Off, 3 = 3s, 10 = 10s
    var countdownValue by remember { mutableIntStateOf(0) }
    var isScreenFlashing by remember { mutableStateOf(false) }
    var isDarkEnvironment by remember { mutableStateOf(false) }

    val activity = context as? Activity
    fun setScreenBrightness(brightness: Float) {
        try {
            activity?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = brightness
                window.attributes = lp
            }
        } catch (_: Exception) {}
    }

    // Ambient Light Sensor for Auto Night Light Enhancement (Instagram-like Night Mode)
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNightTime = hour >= 19 || hour < 6

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val lux = event?.values?.getOrNull(0) ?: 100f
                isDarkEnvironment = lux < 25f || isNightTime
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (lightSensor != null) {
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            isDarkEnvironment = isNightTime
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
            setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Permissions Launcher
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    // Bind CameraX
    LaunchedEffect(hasCameraPermission, isFrontCamera, selectedMediaUri, isGradientStoryMode) {
        if (hasCameraPermission && selectedMediaUri == null && !isGradientStoryMode) {
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    val preferredLens = if (isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    val fallbackLens = if (isFrontCamera) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT

                    val selector = when {
                        provider.hasCamera(CameraSelector.Builder().requireLensFacing(preferredLens).build()) ->
                            CameraSelector.Builder().requireLensFacing(preferredLens).build()
                        provider.hasCamera(CameraSelector.Builder().requireLensFacing(fallbackLens).build()) ->
                            CameraSelector.Builder().requireLensFacing(fallbackLens).build()
                        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> null
                    }

                    if (selector != null) {
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            imageCapture
                        )
                        activeCamera = camera
                        if (flashMode == 3 && camera.cameraInfo.hasFlashUnit()) {
                            camera.cameraControl.enableTorch(true)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("StoryCamera", "Camera binding failed", e)
                }
            }, executor)
        } else {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {}
            activeCamera = null
        }
    }

    // Flash & Night Glow Controller
    LaunchedEffect(flashMode, activeCamera, isDarkEnvironment, isFrontCamera) {
        val cam = activeCamera
        try {
            if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(flashMode == 3)
            }
        } catch (_: Exception) {}

        imageCapture.flashMode = when (flashMode) {
            1 -> ImageCapture.FLASH_MODE_ON
            2 -> if (isDarkEnvironment) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }

        val shouldEnhanceScreenLight = flashMode == 4 || (flashMode == 2 && isDarkEnvironment)
        if (shouldEnhanceScreenLight) {
            setScreenBrightness(1.0f) // Instagram Night Screen Glow & Auto Night Light Enhance
        } else {
            setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val type = context.contentResolver.getType(uri) ?: ""
            val isVid = type.startsWith("video") || uri.toString().contains("video") || uri.toString().endsWith(".mp4")
            selectedMediaUri = uri
            isVideo = isVid
            SoundManager.playClickSound()
        }
    }

    // Shutter Trigger Helper
    fun capturePhoto() {
        SoundManager.playClickSound()
        val isNightScreenFlash = flashMode == 4 || (flashMode == 2 && isDarkEnvironment) || (isFrontCamera && (flashMode == 1 || isDarkEnvironment))
        val photoFile = File(context.cacheDir, "story_${System.currentTimeMillis()}.jpg")

        scope.launch {
            if (isNightScreenFlash) {
                setScreenBrightness(1.0f)
                isScreenFlashing = true
                delay(250) // Illuminate face with warm bright light for HD night photo
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
            imageCapture.takePicture(
                outputOptions,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (isNightScreenFlash) {
                            isScreenFlashing = false
                            val shouldKeepGlow = flashMode == 4 || (flashMode == 2 && isDarkEnvironment)
                            if (!shouldKeepGlow) {
                                setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                            }
                        }
                        selectedMediaUri = Uri.fromFile(photoFile)
                        isVideo = false
                    }

                    override fun onError(exc: ImageCaptureException) {
                        // Fallback to previewView.bitmap (safely captures frame on emulators)
                        try {
                            val bitmap = previewView.bitmap
                            if (bitmap != null) {
                                FileOutputStream(photoFile).use { fos ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                                }
                                selectedMediaUri = Uri.fromFile(photoFile)
                                isVideo = false
                            } else {
                                Toast.makeText(context, "Photo captured!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
                        } finally {
                            if (isNightScreenFlash) {
                                isScreenFlashing = false
                                val shouldKeepGlow = flashMode == 4 || (flashMode == 2 && isDarkEnvironment)
                                if (!shouldKeepGlow) {
                                    setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    // Story Upload Action
    fun publishStory(isCloseFriends: Boolean) {
        if (isPublishingStory) return
        val effectiveExclusiveStory = isVerifiedCreator && isExclusiveStory
        val minExPrice = monetizationSettings.exclusiveMinPriceInr.coerceAtLeast(10.0)
        val maxExPrice = monetizationSettings.exclusiveMaxPriceInr.coerceAtLeast(minExPrice)
        val parsedExPrice = if (effectiveExclusiveStory) {
            val p = exclusiveStoryPriceText.toDoubleOrNull()
            if (p == null || p < minExPrice || p > maxExPrice) {
                Toast.makeText(
                    context,
                    "Set Exclusive Story price between ₹${minExPrice.toInt()} and ₹${maxExPrice.toInt()}",
                    Toast.LENGTH_SHORT
                ).show()
                showExclusiveStoryConfig = true
                return
            }
            p
        } else 0.0

        SoundManager.playClickSound()
        publishErrorMessage = null
        isPublishingStory = true
        GlobalState.isPostingStory.value = true

        // Serialize all stickers to JSON
        val stickersJson = JSONArray().apply {
            stickerElements.forEach { el ->
                val s = el.sticker
                put(JSONObject().apply {
                    put("id", el.id)
                    put("type", if (!el.gifUrl.isNullOrEmpty()) "gif" else s.type)
                    put("question", if (!el.gifUrl.isNullOrEmpty()) el.gifUrl else s.question)
                    put("options", JSONArray(s.options))
                    put("correctOptionIndex", s.correctOptionIndex)
                    put("emoji", s.emoji)
                    put("mentionUsername", s.mentionUsername)
                    put("hashtag", s.hashtag)
                    put("locationName", s.locationName)
                    put("addYoursPrompt", s.addYoursPrompt)
                    put("xPercent", (el.offsetX / 400f).coerceIn(-0.5f, 0.5f) + 0.5f)
                    put("yPercent", (el.offsetY / 700f).coerceIn(-0.5f, 0.5f) + 0.5f)
                    put("rotation", el.rotation.toDouble())
                    put("scale", el.scale.toDouble())
                })
            }
        }.toString()

        val fullTextOverlay = textElements.joinToString(separator = "\n") { it.text }

        storyViewModel.uploadStory(
            mediaUri = selectedMediaUri,
            textOverlay = fullTextOverlay,
            musicTrackUrl = selectedMusicTrack?.previewUrl,
            musicTrackName = selectedMusicTrack?.name,
            musicTrackArtist = selectedMusicTrack?.artistName,
            mentionedUserId = mentionedUserId,
            mentionedUsername = mentionedUsername,
            isReback = isReback,
            repostedPostId = repostedPostId,
            isCloseFriendsOnly = isCloseFriends,
            stickersJson = stickersJson,
            isExclusiveContent = effectiveExclusiveStory,
            exclusivePrice = parsedExPrice,
            teaserCaption = if (effectiveExclusiveStory) exclusiveStoryTeaser.trim() else "",
            appContext = context.applicationContext,
            onSuccess = {
                isPublishingStory = false
                publishErrorMessage = null
                GlobalState.isPostingStory.value = false
                Toast.makeText(context, "Story shared!", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onError = { err ->
                isPublishingStory = false
                GlobalState.isPostingStory.value = false
                publishErrorMessage = err
                Toast.makeText(context, "Story upload failed: $err", Toast.LENGTH_LONG).show()
            }
        )
    }

    Dialog(
        onDismissRequest = { if (!isPublishingStory) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isPublishingStory
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val isEditorMode = selectedMediaUri != null || isGradientStoryMode

            // Main Content: Live Camera Preview OR Interactive Canvas
            var cameraZoomRatio by remember { mutableFloatStateOf(1f) }
            if (!isEditorMode && !isDrawingMode && !isEditingText) {
                // Live Camera Viewport with Pinch-to-Zoom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeCamera) {
                            detectTransformGestures { _, _, zoom, _ ->
                                val cam = activeCamera
                                if (cam != null && zoom != 1f) {
                                    val maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 5f
                                    val minZoom = cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
                                    cameraZoomRatio = (cameraZoomRatio * zoom).coerceIn(minZoom, maxZoom)
                                    cam.cameraControl.setZoomRatio(cameraZoomRatio)
                                }
                            }
                        }
                ) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (cameraZoomRatio > 1.05f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 175.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        cameraZoomRatio = 1f
                                        activeCamera?.cameraControl?.setZoomRatio(1f)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1fx", cameraZoomRatio),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Camera permission is required", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEE1D52))
                                ) {
                                    Text("Grant Permission", color = Color.White)
                                }
                            }
                        }
                    }

                    // Live Filter Tint Overlay
                    val activeFilterTint = STORY_FILTER_TINTS.getOrElse(activeFilterIndex) { Color.Transparent }
                    if (activeFilterTint != Color.Transparent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(activeFilterTint)
                        )
                    }

                    // Instagram Night Selfie Screen Glow Frame (Warm illumination ring)
                    val showNightGlowRing = flashMode == 4 || (flashMode == 2 && isDarkEnvironment)
                    if (showNightGlowRing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(20.dp, Color(0xFFFFECC0).copy(alpha = 0.9f))
                        )
                    }

                    // Auto Night Mode Badge Indicator (Instagram style)
                    if (isDarkEnvironment && (flashMode == 2 || flashMode == 4)) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 58.dp)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Nightlight, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                            Text("Night Mode • HD Light Auto-Enhanced", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Screen Flash Burst during capture
                    if (isScreenFlashing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFFFBEA))
                        )
                    }
                }
            } else {
                // Interactive Canvas with media/gradient, drawing, text, and stickers
                StoryInteractiveCanvas(
                    mediaUri = selectedMediaUri,
                    isVideo = isVideo,
                    canvasGradient = gradientPresets[activeGradientIndex % gradientPresets.size],
                    activeFilterTint = STORY_FILTER_TINTS[activeFilterIndex % STORY_FILTER_TINTS.size],
                    drawingStrokes = drawingStrokes,
                    textElements = textElements,
                    stickerElements = stickerElements,
                    selectedMusic = selectedMusicTrack,
                    isMusicPlaying = isMusicPlaying,
                    onToggleMusicPlay = { isMusicPlaying = !isMusicPlaying },
                    onRemoveMusic = {
                        selectedMusicTrack = null
                        isMusicPlaying = false
                    },
                    onEditTextElement = { el ->
                        editingTextItem = el
                        isEditingText = true
                    },
                    onDeleteTextElement = { id ->
                        textElements = textElements.filter { it.id != id }
                    },
                    onDeleteStickerElement = { id ->
                        stickerElements = stickerElements.filter { it.id != id }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Action Toolbar (when not in full-screen drawing or text edit modes)
            if (!isDrawingMode && !isEditingText) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close / Retake Button
                    IconButton(
                        onClick = {
                            SoundManager.playClickSound()
                            if (isEditorMode) {
                                selectedMediaUri = null
                                isVideo = false
                                isGradientStoryMode = false
                                textElements = emptyList()
                                stickerElements = emptyList()
                                drawingStrokes = emptyList()
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            if (isEditorMode) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    // Action Icons Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flash / Night Light Toggle (in Camera Mode)
                        if (!isEditorMode) {
                            IconButton(
                                onClick = {
                                    SoundManager.playClickSound()
                                    flashMode = (flashMode + 1) % 5
                                    when (flashMode) {
                                        0 -> Toast.makeText(context, "Flash Off", Toast.LENGTH_SHORT).show()
                                        1 -> Toast.makeText(context, "Flash On", Toast.LENGTH_SHORT).show()
                                        2 -> Toast.makeText(context, "Auto Flash", Toast.LENGTH_SHORT).show()
                                        3 -> Toast.makeText(context, "Back Flashlight On", Toast.LENGTH_SHORT).show()
                                        4 -> Toast.makeText(context, "Night Screen Glow Active", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(
                                    when (flashMode) {
                                        1 -> Icons.Default.FlashOn
                                        2 -> Icons.Default.FlashAuto
                                        3 -> Icons.Default.Highlight
                                        4 -> Icons.Default.WbSunny
                                        else -> Icons.Default.FlashOff
                                    },
                                    contentDescription = "Flash",
                                    tint = when (flashMode) {
                                        1 -> Color(0xFFFFC107)
                                        2 -> Color(0xFF10B981)
                                        3 -> Color(0xFFFF9800)
                                        4 -> Color(0xFFFFD54F)
                                        else -> Color.White
                                    }
                                )
                            }

                            // Timer Toggle (Off / 3s / 10s)
                            IconButton(
                                onClick = {
                                    SoundManager.playClickSound()
                                    timerSetting = when (timerSetting) {
                                        0 -> 3
                                        3 -> 10
                                        else -> 0
                                    }
                                    if (timerSetting > 0) {
                                        Toast.makeText(context, "Timer: ${timerSetting}s", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Timer Off", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Timer, contentDescription = "Timer", tint = if (timerSetting > 0) Color(0xFF10B981) else Color.White)
                                    if (timerSetting > 0) {
                                        Text(
                                            "${timerSetting}s",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(top = 10.dp)
                                        )
                                    }
                                }
                            }

                            // Canvas Gradient Creator Switcher
                            IconButton(
                                onClick = {
                                    SoundManager.playClickSound()
                                    activeGradientIndex = (activeGradientIndex + 1) % gradientPresets.size
                                    isGradientStoryMode = true
                                    Toast.makeText(context, "Canvas Mode", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(Icons.Default.ColorLens, contentDescription = "Canvas Color", tint = Color.White)
                            }
                        }

                        // Music Picker Button
                        IconButton(
                            onClick = {
                                SoundManager.playClickSound()
                                showMusicPicker = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Music",
                                tint = if (selectedMusicTrack != null) Color(0xFFEE1D52) else Color.White
                            )
                        }

                        // Text Tool (Aa)
                        IconButton(
                            onClick = {
                                SoundManager.playClickSound()
                                editingTextItem = null
                                isEditingText = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = "Add Text", tint = Color.White)
                        }

                        // Stickers & GIFs Tool
                        IconButton(
                            onClick = {
                                SoundManager.playClickSound()
                                showStickerSheet = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.Mood, contentDescription = "Stickers", tint = Color.White)
                        }

                        // Draw Tool (Pen)
                        IconButton(
                            onClick = {
                                SoundManager.playClickSound()
                                isDrawingMode = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.Brush, contentDescription = "Draw", tint = Color.White)
                        }

                        // Filters Tool
                        IconButton(
                            onClick = {
                                SoundManager.playClickSound()
                                showFiltersCarousel = !showFiltersCarousel
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Filters",
                                tint = if (activeFilterIndex > 0) Color(0xFFFFC107) else Color.White
                            )
                        }

                        // Exclusive Pay-to-Unlock Story Toggle Button
                        IconButton(
                            onClick = {
                                SoundManager.playClickSound()
                                showExclusiveStoryConfig = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isExclusiveStory) Color(0xFFFFC107) else Color.Black.copy(alpha = 0.45f),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (isExclusiveStory) Color(0xFFFFE082) else Color.White.copy(alpha = 0.25f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Exclusive Story",
                                tint = if (isExclusiveStory) Color.Black else Color(0xFFFFD54F),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Save to Device Button
                        if (isEditorMode) {
                            IconButton(
                                onClick = {
                                    SoundManager.playClickSound()
                                    Toast.makeText(context, "Story saved to device!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Save", tint = Color.White)
                            }
                        }
                    }
                }
            }

            // Filter Carousel Overlay (when opened)
            if (showFiltersCarousel && !isDrawingMode && !isEditingText) {
                LazyRow(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    itemsIndexed(STORY_FILTER_NAMES) { idx, name ->
                        val isSel = activeFilterIndex == idx
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                SoundManager.playClickSound()
                                activeFilterIndex = idx
                            },
                            label = { Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFEE1D52),
                                selectedLabelColor = Color.White,
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                labelColor = Color.White
                            ),
                            border = null
                        )
                    }
                }
            }

            // Countdown Timer Overlay
            if (countdownValue > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    val scale by animateFloatAsState(
                        targetValue = 1.3f,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )
                    Text(
                        text = "$countdownValue",
                        fontSize = 130.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.scale(scale)
                    )
                }
            }

            // Bottom Controls Area
            if (!isDrawingMode && !isEditingText) {
                if (!isEditorMode) {
                    // CAMERA MODE BOTTOM BAR: Gallery Thumbnail, Shutter Button, Flip Camera
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gallery Media Picker Button
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(1.5.dp, Color.White, RoundedCornerShape(14.dp))
                                .clickable {
                                    SoundManager.playClickSound()
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        // Big Shutter Button (Tap = Photo, Long Press = Video Record)
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 4.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .padding(6.dp)
                                .clickable {
                                    if (timerSetting > 0) {
                                        scope.launch {
                                            countdownValue = timerSetting
                                            while (countdownValue > 0) {
                                                SoundManager.playClickSound()
                                                delay(1000)
                                                countdownValue--
                                            }
                                            capturePhoto()
                                        }
                                    } else {
                                        capturePhoto()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }

                        // Flip Front/Back Camera
                        IconButton(
                            onClick = {
                                SoundManager.playClickSound()
                                isFrontCamera = !isFrontCamera
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(1.5.dp, Color.White, CircleShape)
                        ) {
                            Icon(Icons.Default.FlipCameraIos, contentDescription = "Flip Camera", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    // MEDIA READY BOTTOM BAR: Your Story + Close Friends
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "Your Story" Button (Public / Followers)
                        Button(
                            onClick = { publishStory(isCloseFriends = false) },
                            enabled = !isPublishingStory,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(26.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("publish_your_story_button"),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(model = "https://api.dicebear.com/7.x/avataaars/png?seed=${RepositoryManager.getCurrentUserId()}"),
                                    contentDescription = "My Avatar",
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Your Story", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            }
                        }

                        // "Close Friends" Button (Vibrant Green Pill with Star)
                        Button(
                            onClick = { publishStory(isCloseFriends = true) },
                            enabled = !isPublishingStory,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(26.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("publish_close_friends_button"),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color.White.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Close Friends", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            }
                        }
                    }

                    val activeStoryError = publishErrorMessage
                    if (!activeStoryError.isNullOrBlank() && !isPublishingStory) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF2B1216).copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.7f)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = if (isExclusiveStory) 128.dp else 84.dp)
                                .fillMaxWidth()
                                .testTag("story_upload_error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(20.dp))
                                Text(
                                    text = activeStoryError,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { publishStory(isCloseFriends = false) }) {
                                    Text("Retry", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Active Exclusive Story Pill Indicator above bottom bar
                    if (isExclusiveStory) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFFFC107),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 82.dp)
                                .clickable { showExclusiveStoryConfig = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "Exclusive Story • ₹${exclusiveStoryPriceText.ifBlank { "29" }} (Tap to edit)",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            if (showExclusiveStoryConfig) {
                val minP = monetizationSettings.exclusiveMinPriceInr.coerceAtLeast(10.0)
                val maxP = monetizationSettings.exclusiveMaxPriceInr.coerceAtLeast(minP)
                val keepPct = (100.0 - monetizationSettings.exclusiveCommissionPct.coerceIn(0.0, 50.0)).toInt()
                AlertDialog(
                    onDismissRequest = { showExclusiveStoryConfig = false },
                    containerColor = Color(0xFF16161C),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFFC107))
                            Text("Exclusive Story", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!isVerifiedCreator) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1E88E5).copy(alpha = 0.16f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                        Text(
                                            text = "Verification Badge Required. You can enable Exclusive Stories once your account receives a Verification Badge.",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Make Story Exclusive", color = Color.White.copy(alpha = if (isVerifiedCreator) 1f else 0.5f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        if (isVerifiedCreator) "Viewers pay to unlock • Keep $keepPct%"
                                        else "Locked • Requires Verification Badge",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = isVerifiedCreator && isExclusiveStory,
                                    enabled = isVerifiedCreator,
                                    onCheckedChange = { if (isVerifiedCreator) isExclusiveStory = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = Color(0xFFFFC107)
                                    )
                                )
                            }
                            if (isVerifiedCreator && isExclusiveStory) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf("10", "29", "49", "99").forEach { opt ->
                                        val sel = exclusiveStoryPriceText == opt
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (sel) Color(0xFFFFC107) else Color(0xFF252530),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { exclusiveStoryPriceText = opt }
                                        ) {
                                            Text(
                                                "₹$opt",
                                                color = if (sel) Color.Black else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = exclusiveStoryPriceText,
                                    onValueChange = { txt ->
                                        val clean = txt.filter { it.isDigit() }
                                        if (clean.length <= 5) exclusiveStoryPriceText = clean
                                    },
                                    label = { Text("Price (₹${minP.toInt()} – ₹${maxP.toInt()})") },
                                    prefix = { Text("₹ ", color = Color(0xFFFFC107), fontWeight = FontWeight.Bold) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = exclusiveStoryTeaser,
                                    onValueChange = { if (it.length <= 120) exclusiveStoryTeaser = it },
                                    label = { Text("Public Teaser Caption (Optional)") },
                                    placeholder = { Text("Preview text shown over blurred story") },
                                    maxLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showExclusiveStoryConfig = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
                        ) {
                            Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Full-screen Drawing Mode Overlay
            if (isDrawingMode) {
                StoryDrawOverlay(
                    strokes = drawingStrokes,
                    currentPoints = currentStrokePoints,
                    selectedColor = activeBrushColor,
                    selectedStrokeWidth = activeBrushWidth,
                    onPointAdded = { pt ->
                        currentStrokePoints = currentStrokePoints + pt
                    },
                    onStrokeFinished = {
                        if (currentStrokePoints.isNotEmpty()) {
                            drawingStrokes = drawingStrokes + DrawnStroke(
                                points = currentStrokePoints,
                                color = activeBrushColor,
                                strokeWidth = activeBrushWidth
                            )
                            currentStrokePoints = emptyList()
                        }
                    },
                    onColorSelected = { col -> activeBrushColor = col },
                    onStrokeWidthSelected = { w -> activeBrushWidth = w },
                    onUndo = {
                        if (drawingStrokes.isNotEmpty()) {
                            drawingStrokes = drawingStrokes.dropLast(1)
                        }
                    },
                    onClear = {
                        drawingStrokes = emptyList()
                        currentStrokePoints = emptyList()
                    },
                    onDone = {
                        isDrawingMode = false
                    }
                )
            }

            // Full-screen Typography & Text Editor Modal
            if (isEditingText) {
                StoryTextEditorModal(
                    initialText = editingTextItem?.text ?: "",
                    initialColor = editingTextItem?.color ?: Color.White,
                    initialBgColor = editingTextItem?.backgroundColor ?: Color.Transparent,
                    initialFontIndex = editingTextItem?.fontIndex ?: 0,
                    initialAlignment = editingTextItem?.alignment ?: TextAlign.Center,
                    onDone = { txt, col, bgCol, fontIdx, align ->
                        val existingItem = editingTextItem
                        if (existingItem != null) {
                            existingItem.text = txt
                            existingItem.color = col
                            existingItem.backgroundColor = bgCol
                            existingItem.fontIndex = fontIdx
                            existingItem.alignment = align
                        } else {
                            textElements = textElements + StoryTextElement(
                                text = txt,
                                color = col,
                                backgroundColor = bgCol,
                                fontIndex = fontIdx,
                                alignment = align
                            )
                        }
                        isEditingText = false
                        editingTextItem = null
                    },
                    onDismiss = {
                        isEditingText = false
                        editingTextItem = null
                    }
                )
            }

            // Interactive Stickers & GIFs Bottom Sheet
            if (showStickerSheet) {
                StoryStickerPickerSheet(
                    onDismiss = { showStickerSheet = false },
                    onStickerSelected = { sticker ->
                        stickerElements = stickerElements + StoryStickerElement(sticker = sticker)
                    },
                    onGifSelected = { gifUrl ->
                        stickerElements = stickerElements + StoryStickerElement(
                            sticker = StorySticker(
                                id = UUID.randomUUID().toString(),
                                type = "gif",
                                question = gifUrl
                            ),
                            gifUrl = gifUrl
                        )
                    },
                    onOpenPollBuilder = { showPollBuilder = true },
                    onOpenQuestionBuilder = { showQuestionBuilder = true },
                    onOpenQuizBuilder = { showQuizBuilder = true },
                    onOpenSliderBuilder = { showSliderBuilder = true },
                    onOpenAddYoursBuilder = { showAddYoursBuilder = true },
                    onOpenMentionBuilder = { showMentionBuilder = true },
                    onOpenLocationBuilder = { showLocationBuilder = true },
                    onOpenHashtagBuilder = { showHashtagBuilder = true }
                )
            }

            // Music Picker Sheet
            if (showMusicPicker) {
                MusicPickerSheet(
                    onDismiss = { showMusicPicker = false },
                    onTrackSelected = { track ->
                        selectedMusicTrack = track
                        isMusicPlaying = true
                        showMusicPicker = false
                    }
                )
            }

            // Sticker Builder Dialogs
            if (showPollBuilder) {
                PollBuilderDialog(
                    onDismiss = { showPollBuilder = false },
                    onCreatePoll = { q, o1, o2 ->
                        stickerElements = stickerElements + StoryStickerElement(
                            sticker = StorySticker(
                                id = UUID.randomUUID().toString(),
                                type = "poll",
                                question = q,
                                options = listOf(o1, o2)
                            )
                        )
                        showPollBuilder = false
                    }
                )
            }

            if (showQuestionBuilder) {
                QuestionBuilderDialog(
                    onDismiss = { showQuestionBuilder = false },
                    onCreateQuestion = { prompt ->
                        stickerElements = stickerElements + StoryStickerElement(
                            sticker = StorySticker(
                                id = UUID.randomUUID().toString(),
                                type = "question",
                                question = prompt
                            )
                        )
                        showQuestionBuilder = false
                    }
                )
            }

            if (showQuizBuilder) {
                QuizBuilderDialog(
                    onDismiss = { showQuizBuilder = false },
                    onCreateQuiz = { q, opts, correctIdx ->
                        stickerElements = stickerElements + StoryStickerElement(
                            sticker = StorySticker(
                                id = UUID.randomUUID().toString(),
                                type = "quiz",
                                question = q,
                                options = opts,
                                correctOptionIndex = correctIdx
                            )
                        )
                        showQuizBuilder = false
                    }
                )
            }

            if (showSliderBuilder) {
                EmojiSliderBuilderDialog(
                    onDismiss = { showSliderBuilder = false },
                    onCreateSlider = { q, em ->
                        stickerElements = stickerElements + StoryStickerElement(
                            sticker = StorySticker(
                                id = UUID.randomUUID().toString(),
                                type = "slider",
                                question = q,
                                emoji = em
                            )
                        )
                        showSliderBuilder = false
                    }
                )
            }

            if (showAddYoursBuilder) {
                SimpleTextPromptDialog(
                    title = "Add Yours",
                    hint = "e.g. Sunset vibes, Drop your pet...",
                    icon = Icons.Default.AddBox,
                    iconColor = Color(0xFF10B981),
                    onDismiss = { showAddYoursBuilder = false },
                    onSubmit = { prompt ->
                        stickerElements = stickerElements + StoryStickerElement(
                            sticker = StorySticker(
                                id = UUID.randomUUID().toString(),
                                type = "add_yours",
                                addYoursPrompt = prompt
                            )
                        )
                        showAddYoursBuilder = false
                    }
                )
            }

            if (showMentionBuilder) {
                MentionPickerOverlay(
                    selectedUsers = emptyList(),
                    onAddUser = { user: com.example.model.User ->
                        stickerElements = stickerElements + StoryStickerElement(
                            sticker = StorySticker(
                                id = UUID.randomUUID().toString(),
                                type = "mention",
                                mentionUsername = user.username,
                                mentionUserId = user.id,
                                mentionUserAvatar = user.avatarUrl
                            )
                        )
                        showMentionBuilder = false
                    },
                    onRemoveUser = {},
                    onClose = { showMentionBuilder = false }
                )
            }

            if (showLocationBuilder) {
                SimpleTextPromptDialog(
                    title = "Add Location",
                    hint = "City, place or landmark",
                    icon = Icons.Default.LocationOn,
                    iconColor = Color(0xFF00BCD4),
                    onDismiss = { showLocationBuilder = false },
                    onSubmit = { loc ->
                        stickerElements = stickerElements + StoryStickerElement(
                            sticker = StorySticker(
                                id = UUID.randomUUID().toString(),
                                type = "location",
                                locationName = loc
                            )
                        )
                        showLocationBuilder = false
                    }
                )
            }

            if (showHashtagBuilder) {
                SimpleTextPromptDialog(
                    title = "Add Hashtag",
                    hint = "hashtag",
                    prefix = "#",
                    icon = Icons.Default.Tag,
                    iconColor = Color(0xFF3F51B5),
                    onDismiss = { showHashtagBuilder = false },
                    onSubmit = { tag ->
                        stickerElements = stickerElements + StoryStickerElement(
                            sticker = StorySticker(
                                id = UUID.randomUUID().toString(),
                                type = "hashtag",
                                hashtag = tag
                            )
                        )
                        showHashtagBuilder = false
                    }
                )
            }

            if (isPublishingStory) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF16161F),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        shadowElevation = 12.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { uploadProgress.coerceIn(0.05f, 1f) },
                                color = Color(0xFFEE1D52),
                                trackColor = Color.White.copy(alpha = 0.15f),
                                strokeWidth = 3.5.dp,
                                modifier = Modifier.size(46.dp)
                            )
                            Text(
                                text = "Posting story... ${(uploadProgress * 100).toInt().coerceIn(5, 100)}%",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Keeping your media intact until saved",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
