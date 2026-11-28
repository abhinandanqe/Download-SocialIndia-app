package com.example.editor.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.editor.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class VideoEditorViewModel : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val undoStack = mutableListOf<EditorState>()
    private val redoStack = mutableListOf<EditorState>()

    fun processAction(action: EditorAction) {
        // Save state for undo before modifying, except for playback and selection changes
        if (action !is EditorAction.SetPlaying && action !is EditorAction.SeekTo && action !is EditorAction.SelectClip) {
            undoStack.add(_state.value)
            redoStack.clear()
            if (undoStack.size > 20) undoStack.removeAt(0) // limit stack
        }

        when (action) {
            is EditorAction.AddVideoClip -> {
                val newClip = Clip(
                    id = UUID.randomUUID().toString(),
                    uri = action.uri,
                    isVideo = true,
                    durationMs = action.durationMs,
                    startTimeMs = _state.value.totalDurationMs
                )
                _state.update { 
                    it.copy(
                        videoClips = it.videoClips + newClip,
                        totalDurationMs = it.totalDurationMs + action.durationMs
                    ) 
                }
            }
            is EditorAction.UpdateTrim -> {
                _state.update { current ->
                    val updatedClips = current.videoClips.map {
                        if (it.id == action.clipId) {
                            it.copy(trimStartMs = action.startMs, trimEndMs = action.endMs)
                        } else it
                    }
                    current.copy(videoClips = updatedClips)
                }
                recalculateTimelines()
            }
            is EditorAction.RemoveClip -> {
                _state.update { current ->
                    current.copy(videoClips = current.videoClips.filter { it.id != action.clipId })
                }
                recalculateTimelines()
            }
            is EditorAction.SelectClip -> {
                _state.update { it.copy(selectedClipId = action.clipId, selectedTextId = null, selectedAudioId = null) }
            }
            is EditorAction.SeekTo -> {
                _state.update { it.copy(playheadPositionMs = action.positionMs) }
            }
            is EditorAction.SetPlaying -> {
                _state.update { it.copy(isPlaying = action.isPlaying) }
            }
            is EditorAction.AddText -> {
                val newText = TextOverlay(
                    id = UUID.randomUUID().toString(),
                    text = action.text,
                    startTimeMs = action.startMs,
                    durationMs = action.durationMs
                )
                _state.update { it.copy(textOverlays = it.textOverlays + newText) }
            }
            is EditorAction.UpdateText -> {
                _state.update { current ->
                    current.copy(textOverlays = current.textOverlays.map {
                        if (it.id == action.id) it.copy(text = action.text, color = action.color) else it
                    })
                }
            }
            is EditorAction.ApplyFilter -> {
                _state.update { current ->
                    current.copy(videoClips = current.videoClips.map {
                        if (it.id == action.clipId) it.copy(filters = listOf(action.filter)) else it
                    })
                }
            }
            is EditorAction.ChangeSpeed -> {
                _state.update { current ->
                    current.copy(videoClips = current.videoClips.map {
                        if (it.id == action.clipId) it.copy(speed = action.speed) else it
                    })
                }
                recalculateTimelines()
            }
            is EditorAction.AddAudio -> {
                val newAudio = AudioTrack(
                    id = UUID.randomUUID().toString(),
                    uri = action.uri,
                    startTimeMs = _state.value.playheadPositionMs,
                    durationMs = action.durationMs
                )
                _state.update { it.copy(audioTracks = it.audioTracks + newAudio) }
            }
            is EditorAction.UpdateGlobalAdjustments -> {
                _state.update { it.copy(brightness = action.brightness, contrast = action.contrast, saturation = action.saturation) }
            }
        }
    }

    private fun recalculateTimelines() {
        _state.update { current ->
            var currentStartTime = 0L
            val updatedClips = current.videoClips.map { clip ->
                val adjustedDuration = ((clip.trimEndMs - clip.trimStartMs) / clip.speed).toLong()
                val newClip = clip.copy(startTimeMs = currentStartTime)
                currentStartTime += adjustedDuration
                newClip
            }
            current.copy(videoClips = updatedClips, totalDurationMs = currentStartTime)
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(_state.value)
            _state.value = undoStack.removeLast()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(_state.value)
            _state.value = redoStack.removeLast()
        }
    }
}
