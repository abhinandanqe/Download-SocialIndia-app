package com.example.editor.model

import android.net.Uri

data class Clip(
    val id: String,
    val uri: Uri,
    val isVideo: Boolean,
    val durationMs: Long,
    val startTimeMs: Long, // Start time in the overall timeline
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = durationMs,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val filters: List<FilterType> = emptyList(),
    val transitionIn: TransitionType = TransitionType.NONE,
    val transitionOut: TransitionType = TransitionType.NONE
)

enum class FilterType {
    NONE, WARM, COOL, VINTAGE, MONO, CINEMATIC, BRIGHT, SOFT
}

enum class TransitionType {
    NONE, FADE, WIPE, DISSOLVE
}

data class TextOverlay(
    val id: String,
    val text: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val font: String = "Default",
    val color: Int = 0xFFFFFFFF.toInt(),
    val size: Float = 24f,
    val x: Float = 0.5f, // Relative position 0-1
    val y: Float = 0.5f
)

data class AudioTrack(
    val id: String,
    val uri: Uri,
    val startTimeMs: Long,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = durationMs,
    val volume: Float = 1f
)

data class EditorState(
    val videoClips: List<Clip> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val playheadPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val totalDurationMs: Long = 0L,
    val selectedClipId: String? = null,
    val selectedTextId: String? = null,
    val selectedAudioId: String? = null,
    val aspectRatio: Float = 9f / 16f,
    // Global adjustments
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f
)

sealed class EditorAction {
    data class AddVideoClip(val uri: Uri, val durationMs: Long) : EditorAction()
    data class UpdateTrim(val clipId: String, val startMs: Long, val endMs: Long) : EditorAction()
    data class RemoveClip(val clipId: String) : EditorAction()
    data class SelectClip(val clipId: String?) : EditorAction()
    data class SeekTo(val positionMs: Long) : EditorAction()
    data class SetPlaying(val isPlaying: Boolean) : EditorAction()
    data class AddText(val text: String, val startMs: Long, val durationMs: Long) : EditorAction()
    data class UpdateText(val id: String, val text: String, val color: Int) : EditorAction()
    data class ApplyFilter(val clipId: String, val filter: FilterType) : EditorAction()
    data class ChangeSpeed(val clipId: String, val speed: Float) : EditorAction()
    data class AddAudio(val uri: Uri, val durationMs: Long) : EditorAction()
    data class UpdateGlobalAdjustments(val brightness: Float, val contrast: Float, val saturation: Float) : EditorAction()
}
