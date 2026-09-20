package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GoldenClockDial(
    modifier: Modifier = Modifier,
    dialSize: Dp = 260.dp
) {
    var currentMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    // Smooth real-time update loop
    LaunchedEffect(Unit) {
        while (true) {
            currentMillis = System.currentTimeMillis()
            delay(100L) // 10 FPS is buttery smooth and saves battery/CPU
        }
    }

    val cal = remember { Calendar.getInstance() }
    cal.timeInMillis = currentMillis

    val hour = cal.get(Calendar.HOUR)
    val minute = cal.get(Calendar.MINUTE)
    val second = cal.get(Calendar.SECOND)
    val millis = cal.get(Calendar.MILLISECOND)

    val sweepSecond = second + millis / 1000f
    val sweepMinute = minute + sweepSecond / 60f
    val sweepHour = hour + sweepMinute / 60f

    // Cached paints
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#E6C875")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
    }

    val brandPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#C9962F")
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.2f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
    }

    val subBrandPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#A89F8B")
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.3f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
    }

    val numbers = remember { listOf(12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11) }

    Box(
        modifier = modifier.size(dialSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 8.dp.toPx()

            // Update text sizes based on density
            textPaint.textSize = 12.dp.toPx()
            brandPaint.textSize = 10.dp.toPx()
            subBrandPaint.textSize = 6.5.dp.toPx()

            // 1. Dial Base Gradient Ring (Outer Rim)
            val outerBezelBrush = Brush.sweepGradient(
                listOf(
                    Color(0xFFFFF2B2),
                    Color(0xFFD4AF37),
                    Color(0xFF8A6327),
                    Color(0xFFD4AF37),
                    Color(0xFFFFF2B2)
                ),
                center = center
            )

            // Outer metallic rim
            drawCircle(
                brush = outerBezelBrush,
                radius = radius,
                center = center,
                style = Stroke(width = 6.dp.toPx())
            )

            // Dark inner bezel ring
            drawCircle(
                color = Color(0xFF14110A),
                radius = radius - 3.dp.toPx(),
                center = center
            )

            // Inner dark radial face
            val innerDialBrush = Brush.radialGradient(
                listOf(
                    Color(0xFF1F1B12),
                    Color(0xFF110E08),
                    Color(0xFF090704)
                ),
                center = center,
                radius = radius
            )
            drawCircle(
                brush = innerDialBrush,
                radius = radius - 6.dp.toPx(),
                center = center
            )

            // Gold inner track border
            drawCircle(
                color = Color(0x66C9962F),
                radius = radius - 8.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // 2. Dial Ticks & Roman/Standard Numerals
            val tickCount = 60
            for (i in 0 until tickCount) {
                val isMajor = i % 5 == 0
                val angleRad = Math.toRadians((i * 6 - 90).toDouble())
                val tickLength = if (isMajor) 9.dp.toPx() else 4.5.dp.toPx()
                val tickWidth = if (isMajor) 2.5.dp.toPx() else 1.2.dp.toPx()
                val tickColor = if (isMajor) Color(0xFFE6C875) else Color(0x66D4C4A1)

                val startR = radius - 10.dp.toPx()
                val endR = startR - tickLength

                val startX = center.x + (startR * cos(angleRad)).toFloat()
                val startY = center.y + (startR * sin(angleRad)).toFloat()
                val endX = center.x + (endR * cos(angleRad)).toFloat()
                val endY = center.y + (endR * sin(angleRad)).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )
            }

            // Draw Numerals (12, 1, 2... 11)
            val textRadius = radius - 26.dp.toPx()
            drawIntoCanvas { canvas ->
                numbers.forEachIndexed { index, num ->
                    val angleRad = Math.toRadians((index * 30 - 90).toDouble())
                    val numX = center.x + (textRadius * cos(angleRad)).toFloat()
                    val numY = center.y + (textRadius * sin(angleRad)).toFloat() + 4.dp.toPx()
                    canvas.nativeCanvas.drawText(num.toString(), numX, numY, textPaint)
                }

                // Brand text inside dial
                canvas.nativeCanvas.drawText("FOCUS", center.x, center.y - 28.dp.toPx(), brandPaint)
                canvas.nativeCanvas.drawText("LEGENDARY", center.x, center.y - 18.dp.toPx(), subBrandPaint)
            }

            // 3. Hands
            // Hour Hand
            val hourAngleRad = Math.toRadians((sweepHour * 30 - 90).toDouble())
            val hourLength = radius * 0.50f
            val hourEnd = Offset(
                center.x + (hourLength * cos(hourAngleRad)).toFloat(),
                center.y + (hourLength * sin(hourAngleRad)).toFloat()
            )
            val hourBack = Offset(
                center.x - (12.dp.toPx() * cos(hourAngleRad)).toFloat(),
                center.y - (12.dp.toPx() * sin(hourAngleRad)).toFloat()
            )
            drawLine(
                color = Color(0xFFFCF6BA),
                start = hourBack,
                end = hourEnd,
                strokeWidth = 4.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Minute Hand
            val minuteAngleRad = Math.toRadians((sweepMinute * 6 - 90).toDouble())
            val minuteLength = radius * 0.72f
            val minuteEnd = Offset(
                center.x + (minuteLength * cos(minuteAngleRad)).toFloat(),
                center.y + (minuteLength * sin(minuteAngleRad)).toFloat()
            )
            val minuteBack = Offset(
                center.x - (16.dp.toPx() * cos(minuteAngleRad)).toFloat(),
                center.y - (16.dp.toPx() * sin(minuteAngleRad)).toFloat()
            )
            drawLine(
                color = Color(0xFFD4AF37),
                start = minuteBack,
                end = minuteEnd,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Second Hand (Golden needle with counterbalance)
            val secondAngleRad = Math.toRadians((sweepSecond * 6 - 90).toDouble())
            val secondLength = radius * 0.85f
            val secondEnd = Offset(
                center.x + (secondLength * cos(secondAngleRad)).toFloat(),
                center.y + (secondLength * sin(secondAngleRad)).toFloat()
            )
            val secondBack = Offset(
                center.x - (22.dp.toPx() * cos(secondAngleRad)).toFloat(),
                center.y - (22.dp.toPx() * sin(secondAngleRad)).toFloat()
            )
            drawLine(
                color = Color(0xFFFFDF00),
                start = secondBack,
                end = secondEnd,
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Center Pin & Cap
            drawCircle(
                color = Color(0xFF14110A),
                radius = 6.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color(0xFFFFF2B2),
                radius = 4.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color(0xFFC9962F),
                radius = 2.dp.toPx(),
                center = center
            )
        }
    }
}
