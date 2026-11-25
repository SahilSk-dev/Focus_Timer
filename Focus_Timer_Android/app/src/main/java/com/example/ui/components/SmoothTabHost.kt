package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex

/**
 * Keeps all tab subtrees mounted once warmed so switching tabs preserves scroll state
 * and avoids teardown/recomposition jank, while running a light crossfade + slide transition.
 */
@Composable
fun SmoothTabHost(
    selectedIndex: Int,
    tabCount: Int,
    modifier: Modifier = Modifier,
    prewarmAll: Boolean = true,
    slideDistancePx: Float = 28f,
    content: @Composable (index: Int) -> Unit
) {
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    var transitionDirection by remember { mutableIntStateOf(1) }
    if (selectedIndex != previousIndex) {
        transitionDirection = if (selectedIndex > previousIndex) 1 else -1
        previousIndex = selectedIndex
    }

    Box(modifier = modifier.clipToBounds()) {
        for (index in 0 until tabCount) {
            if (!prewarmAll && index != selectedIndex) continue

            val isSelected = index == selectedIndex
            val progress by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 190,
                    easing = FastOutSlowInEasing
                ),
                label = "smooth_tab_progress_$index"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isSelected) 1f else 0f)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            // Skip drawing and hit-testing once faded out while keeping subtree alive.
                            if (progress > 0.01f) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                    }
                    .graphicsLayer {
                        alpha = progress
                        val remaining = 1f - progress
                        translationX = if (isSelected) {
                            transitionDirection * slideDistancePx * remaining
                        } else {
                            -transitionDirection * (slideDistancePx * 0.45f) * remaining
                        }
                    }
            ) {
                content(index)
            }
        }
    }
}
