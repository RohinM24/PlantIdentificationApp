package com.example.roleaf.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Singular animated ConfidenceRing:
 * - No outer static ring by default
 * - Smooth progress animation
 * - Rotating sweep gradient for a "neon" animated look
 * - Pulsing glow beneath progress
 *
 * Use Modifier.fillMaxSize() inside a square parent so it overlays the image exactly.
 */
@Composable
fun ConfidenceRing(
    progressPercent: Int,
    modifier: Modifier = Modifier,
    innerStrokeWidth: Dp = 12.dp,      // visible single ring stroke
    knobRadius: Dp = 6.dp,
    baseColor: Color = Color(0xFF004E1A).copy(alpha = 0.12f), // subtle base track
    progressStart: Color = Color(0xFF89FF9A),
    progressEnd: Color = Color(0xFF0B6B2A)
) {
    val target = (progressPercent.coerceIn(0, 100) / 100f)

    // animated progress from 0 -> target
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
    )

    // rotating sweep for gradient (gives motion even when progress is static)
    val rotationTransition = rememberInfiniteTransition()
    val rotationDeg by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // pulsing glow alpha
    val glowAlpha by rotationTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val innerPx = with(LocalDensity.current) { innerStrokeWidth.toPx() }
    val knobPx = with(LocalDensity.current) { knobRadius.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val minDim = size.minDimension
        if (minDim <= 0f) return@Canvas

        val cx = size.width / 2f
        val cy = size.height / 2f

        // inner geometry
        val inset = innerPx / 2f + 2f
        val diameter = minDim - 2f * inset
        val topLeft = Offset(inset, inset)
        val innerSize = Size(diameter.coerceAtLeast(0f), diameter.coerceAtLeast(0f))

        // 1) subtle base track
        drawArc(
            color = baseColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = innerSize,
            style = Stroke(width = innerPx, cap = StrokeCap.Round)
        )

        // 2) pulsating glow behind the progress arc (slightly thicker, blurred look simulated by alpha)
        if (animatedProgress > 0f) {
            drawArc(
                color = progressEnd.copy(alpha = glowAlpha),
                startAngle = -90f + rotationDeg,
                sweepAngle = (360f * animatedProgress).coerceAtMost(360f),
                useCenter = false,
                topLeft = topLeft,
                size = innerSize,
                style = Stroke(width = innerPx * 1.9f, cap = StrokeCap.Round)
            )
        }

        // 3) progress arc with rotating sweep gradient
        val sweep = 360f * animatedProgress.coerceIn(0f, 1f)
        if (sweep > 0f) {
            val brush = Brush.sweepGradient(listOf(progressStart, progressEnd))
            // rotate the gradient visually by offsetting startAngle using rotationDeg
            val startAngle = -90f + rotationDeg
            drawArc(
                brush = brush,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = innerSize,
                style = Stroke(width = innerPx, cap = StrokeCap.Round)
            )

            // 4) knob at the end of arc
            val radiusForKnob = innerSize.width / 2f
            val endAngle = startAngle + sweep
            val theta = Math.toRadians(endAngle.toDouble())
            val knobX = cx + (radiusForKnob) * cos(theta).toFloat()
            val knobY = cy + (radiusForKnob) * sin(theta).toFloat()

            drawCircle(
                color = progressEnd,
                radius = knobPx,
                center = Offset(knobX, knobY)
            )
        }
    }
}
