package com.example.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.example.model.GifItem
import com.example.model.StickerItem

/**
 * Unified Social Media Picker wrapping Stickers, GIFs, and Emojis.
 * Backward-compatible with all existing GIF picker locations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifPickerBottomSheet(
    onDismissRequest: () -> Unit,
    onGifSelected: (GifItem) -> Unit,
    onStickerSelected: ((StickerItem) -> Unit)? = null,
    onEmojiSelected: ((String) -> Unit)? = null
) {
    SocialMediaPickerBottomSheet(
        onDismissRequest = onDismissRequest,
        initialTab = MediaPickerTab.STICKER,
        onGifSelected = onGifSelected,
        onStickerSelected = { sticker ->
            if (onStickerSelected != null) {
                onStickerSelected(sticker)
            } else {
                onGifSelected(
                    GifItem(
                        id = sticker.stickerId,
                        title = sticker.name,
                        previewUrl = sticker.mediaUrl.ifEmpty { sticker.thumbnailUrl },
                        fullUrl = sticker.mediaUrl.ifEmpty { sticker.thumbnailUrl }
                    )
                )
            }
        },
        onEmojiSelected = { emoji ->
            onEmojiSelected?.invoke(emoji)
        }
    )
}
