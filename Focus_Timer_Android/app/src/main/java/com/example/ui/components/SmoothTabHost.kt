package com.example.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

private val SilkEaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Hardware-composited tab host that keeps warmed tab subtrees laid out at (0, 0)
 * and animates strictly on the RenderNode graphicsLayer (zero layout/placement invalidation
 * per animation frame) for butter-smooth 60/120fps tab switches.
 */
@Composable
fun SmoothTabHost(
    selectedIndex: Int,
    tabCount: Int,
    modifier: Modifier = Modifier,
    prewarmAll: Boolean = true,
    warmedCount: Int = if (prewarmAll) tabCount else 1,
    slideDistancePx: Float = 44f,
    content: @Composable (index: Int) -> Unit
) {
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    var transitionDirection by remember { mutableIntStateOf(1) }
    if (selectedIndex != previousIndex) {
        transitionDirection = if (selectedIndex > previousIndex) 1 else -1
        previousIndex = selectedIndex
    }

    val effectiveWarmCount = warmedCount.coerceIn(1, tabCount)

    Box(modifier = modifier.clipToBounds()) {
        for (index in 0 until tabCount) {
            if (index >= effectiveWarmCount && index != selectedIndex) continue

            val isSelected = index == selectedIndex
            val progressState = animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = SilkEaseOut
                ),
                label = "smooth_tab_progress_$index"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isSelected) 1f else 0f)
                    .drawWithContent {
                        if (progressState.value > 0.005f) {
                            drawContent()
                        }
                    }
                    .graphicsLayer {
                        val p = progressState.value
                        val remaining = 1f - p
                        alpha = p
                        translationX = if (isSelected) {
                            transitionDirection * slideDistancePx * remaining
                        } else {
                            -transitionDirection * (slideDistancePx * 0.28f) * remaining
                        }
                        if (isSelected && remaining > 0.001f) {
                            val s = 0.985f + 0.015f * p
                            scaleX = s
                            scaleY = s
                        }
                        compositingStrategy = if (p in 0.005f..0.995f) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                    }
            ) {
                content(index)
            }
        }
    }
}
