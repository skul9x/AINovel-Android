package com.ainovel.audiobook.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ainovel.audiobook.player.WaveformSampler
import com.ainovel.audiobook.ui.theme.NeonCyan
import com.ainovel.audiobook.ui.theme.NeonLavender
import com.ainovel.audiobook.ui.theme.NeonPurple
import kotlin.math.sin

/**
 * High-performance 40-bar dynamic visualizer matching mockup-driven UI.
 * Renders exactly [barCount] rounded vertical bars with smooth animation when playing.
 */
@Composable
fun WaveformVisualizer(
    modifier: Modifier = Modifier,
    amplitudes: FloatArray = FloatArray(WaveformSampler.DEFAULT_BAR_COUNT) { 0.2f },
    progress: Float = 0f,
    isPlaying: Boolean = false,
    barCount: Int = WaveformSampler.DEFAULT_BAR_COUNT,
    activeColor: Color = NeonPurple,
    inactiveColor: Color = NeonLavender.copy(alpha = 0.35f),
    canvasHeight: Dp = 64.dp
) {
    // Micro-animation pulsing phase when playing
    val infiniteTransition = rememberInfiniteTransition(label = "WaveformPulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f, // 2 * PI
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulsePhase"
    )

    // Ensure valid bar array of exact barCount
    val safeAmplitudes = if (amplitudes.size == barCount) {
        amplitudes
    } else {
        FloatArray(barCount) { index ->
            if (amplitudes.isNotEmpty()) {
                val mappedIndex = (index.toFloat() / barCount * amplitudes.size).toInt().coerceIn(0, amplitudes.size - 1)
                amplitudes[mappedIndex]
            } else {
                0.2f
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        val totalWidth = size.width
        val maxHeight = size.height
        val barSpacingRatio = 0.35f
        val slotWidth = totalWidth / barCount
        val barWidth = slotWidth * (1f - barSpacingRatio)
        val spacing = slotWidth * barSpacingRatio
        val minBarHeight = 4.dp.toPx()
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        val activeIndex = (progress.coerceIn(0f, 1f) * barCount).toInt()

        for (i in 0 until barCount) {
            val baseAmp = safeAmplitudes[i].coerceIn(0.05f, 1f)

            // Dynamic live oscillation while playing
            val dynamicAmp = if (isPlaying) {
                val waveOffset = sin(pulsePhase + i * 0.3f).toFloat() * 0.15f
                (baseAmp + waveOffset).coerceIn(0.08f, 1f)
            } else {
                baseAmp
            }

            val currentBarHeight = (dynamicAmp * (maxHeight - minBarHeight) + minBarHeight)
            val left = i * slotWidth + (spacing / 2f)
            val top = (maxHeight - currentBarHeight) / 2f

            val isPlayed = i <= activeIndex
            val barBrush = if (isPlayed) {
                Brush.verticalGradient(
                    colors = listOf(NeonCyan, activeColor),
                    startY = top,
                    endY = top + currentBarHeight
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(inactiveColor, inactiveColor.copy(alpha = 0.2f)),
                    startY = top,
                    endY = top + currentBarHeight
                )
            }

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(left, top),
                size = Size(barWidth, currentBarHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}
