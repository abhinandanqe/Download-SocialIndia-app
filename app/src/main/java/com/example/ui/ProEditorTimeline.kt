package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import com.example.api.ITunesTrack
import com.example.ui.editor.EditorExportResult
import com.example.ui.editor.EditorProjectSnapshot
import com.example.ui.editor.InstagramProEditorScreen

/**
 * Backward-compatible entry point delegating directly to the upgraded
 * full-screen dark gesture-first Instagram-style Pro Editor.
 */
@Composable
fun ProEditorTimeline(
    context: Context,
    selectedMediaIsVideo: Boolean,
    selectedMediaUrl: String?,
    selectedMediaUris: List<Uri>,
    selectedMusic: ITunesTrack?,
    onNavigateBack: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigateToMusicPicker: () -> Unit,
    initialDraft: EditorProjectSnapshot? = null,
    onExportComplete: ((EditorExportResult) -> Unit)? = null
) {
    InstagramProEditorScreen(
        context = context,
        selectedMediaIsVideo = selectedMediaIsVideo,
        selectedMediaUrl = selectedMediaUrl,
        selectedMediaUris = selectedMediaUris,
        selectedMusic = selectedMusic,
        initialDraft = initialDraft,
        onNavigateBack = onNavigateBack,
        onNavigateNext = onNavigateNext,
        onNavigateToMusicPicker = onNavigateToMusicPicker,
        onExportComplete = onExportComplete
    )
}
