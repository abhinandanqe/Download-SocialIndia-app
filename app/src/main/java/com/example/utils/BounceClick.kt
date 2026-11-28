package com.example.utils

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import com.example.ui.motion.SocialMotion
import com.example.ui.motion.rememberReducedMotionEnabled
import kotlinx.coroutines.launch

/**
 * Instagram-style GPU-only press-scale micro-animation (0.92 -> 1.0) with light haptic tick.
 * Respects system reduced-motion setting and animates only transform via graphicsLayer.
 */
fun Modifier.bounceClick(
    enableHaptics: Boolean = true,
    pressedScale: Float = SocialMotion.PressScale
): Modifier = composed {
    val reducedMotion = rememberReducedMotionEnabled()
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(enableHaptics, reducedMotion, pressedScale) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (enableHaptics) {
                    try {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    } catch (_: Exception) {}
                }
                if (!reducedMotion) {
                    scope.launch {
                        scale.animateTo(
                            targetValue = pressedScale,
                            animationSpec = SocialMotion.floatSpec(SocialMotion.DurationQuick)
                        )
                    }
                }
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                if (!reducedMotion) {
                    scope.launch {
                        scale.animateTo(
                            targetValue = 1.0f,
                            animationSpec = SocialMotion.springSnappy()
                        )
                    }
                }
            }
        }
}
