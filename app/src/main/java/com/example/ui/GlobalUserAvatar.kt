package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlobalUserAvatar(
    userId: String,
    avatarUrl: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLiveClick: ((liveStoryId: String) -> Unit)? = null
) {
    // We will implement this
}
