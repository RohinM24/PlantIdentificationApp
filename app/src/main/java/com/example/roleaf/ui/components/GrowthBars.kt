package com.example.roleaf.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.roleaf.R

@Composable
fun GrowthBar(
    label: String,
    valueOutOf10: Int
) {
    val context = LocalContext.current

    val accentGreen = Color(ContextCompat.getColor(context, R.color.accent_green))
    val emeraldLight = Color(ContextCompat.getColor(context, R.color.emerald_light))
    val emeraldDark = Color(ContextCompat.getColor(context, R.color.emerald_dark))
    val onDark = Color(ContextCompat.getColor(context, R.color.on_dark))

    // Progress animation
    val targetValue = valueOutOf10.coerceIn(0, 10) / 10f
    val animatedProgress by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
    )

    // Pulsating glow (breathing) - sync 1800ms
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Shimmer sweep (3500ms)
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = onDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${(targetValue * 10).toInt()}/10",
                color = onDark.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val fullWidth = size.width
            val height = size.height
            val progressWidth = fullWidth * animatedProgress

            // Background track
            drawRoundRect(
                color = emeraldDark.copy(alpha = 0.4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(50f, 50f),
                size = Size(fullWidth, height)
            )

            // Base gradient brush
            val gradientBrush = Brush.horizontalGradient(
                colors = listOf(
                    emeraldLight.copy(alpha = 0.9f),
                    accentGreen.copy(alpha = 0.9f),
                    emeraldDark.copy(alpha = 0.9f)
                ),
                startX = 0f,
                endX = progressWidth
            )

            drawRoundRect(
                brush = gradientBrush,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(50f, 50f),
                size = Size(progressWidth, height)
            )

            // Pulsating glow overlay
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentGreen.copy(alpha = glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(progressWidth, height / 2f),
                    radius = 120f
                ),
                size = Size(progressWidth, height * 1.6f),
                topLeft = Offset(0f, -height * 0.3f)
            )

            // Shimmer sweep overlay
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.35f),
                    Color.Transparent
                ),
                start = Offset(shimmerOffset, 0f),
                end = Offset(shimmerOffset + 150f, height)
            )

            drawRoundRect(
                brush = shimmerBrush,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(50f, 50f),
                size = Size(progressWidth, height)
            )
        }
    }
}








