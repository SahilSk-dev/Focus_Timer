package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

private data class Particle(
    val startXRatio: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val rotationSpeed: Float,
    val isCircle: Boolean
)

@Composable
fun ConfettiEffect(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {}
) {
    val progress = remember { Animatable(0f) }
    val particles = remember {
        val colors = listOf(
            Color(0xFFFFDF00),
            Color(0xFFC9962F),
            Color(0xFFFFF8DC),
            Color(0xFFDAA520),
            Color(0xFFFCF6BA),
            Color(0xFF8BA888)
        )
        List(60) {
            Particle(
                startXRatio = Random.nextFloat(),
                speed = 0.8f + Random.nextFloat() * 0.7f,
                size = 8f + Random.nextFloat() * 12f,
                color = colors.random(),
                rotationSpeed = Random.nextFloat() * 360f,
                isCircle = Random.nextBoolean()
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2800, easing = LinearEasing)
        )
        onFinished()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress.value

        particles.forEach { p ->
            val y = t * h * p.speed
            val x = (p.startXRatio * w) + (kotlin.math.sin((t * 6f + p.startXRatio * 10f).toDouble()) * 25f).toFloat()

            if (y <= h) {
                if (p.isCircle) {
                    drawCircle(
                        color = p.color,
                        radius = p.size / 2f,
                        center = Offset(x, y)
                    )
                } else {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(x - p.size / 2f, y - p.size / 2f),
                        size = Size(p.size, p.size * 0.7f)
                    )
                }
            }
        }
    }
}
