package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val MessengerIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Messenger",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 256f,
        viewportHeight = 256f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(128f, 24f)
            curveTo(68.8f, 24f, 20.8f, 68.8f, 20.8f, 124f)
            curveTo(20.8f, 156f, 36.8f, 185.6f, 62.4f, 204.8f)
            lineTo(62.4f, 240f)
            lineTo(97.6f, 220.8f)
            curveTo(107.2f, 222.4f, 117.6f, 224f, 128f, 224f)
            curveTo(187.2f, 224f, 235.2f, 179.2f, 235.2f, 124f)
            curveTo(235.2f, 68.8f, 187.2f, 24f, 128f, 24f)
            close()
            moveTo(134.4f, 160f)
            lineTo(104f, 128f)
            lineTo(44.8f, 160f)
            lineTo(110.4f, 91.2f)
            lineTo(142.4f, 123.2f)
            lineTo(200f, 91.2f)
            lineTo(134.4f, 160f)
            close()
        }
    }.build()
