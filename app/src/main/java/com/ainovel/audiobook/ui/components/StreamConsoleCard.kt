package com.ainovel.audiobook.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ainovel.audiobook.R
import com.ainovel.audiobook.ui.theme.DarkBackground
import com.ainovel.audiobook.ui.theme.DarkSurface
import com.ainovel.audiobook.ui.theme.DarkSurfaceBorder
import com.ainovel.audiobook.ui.theme.DarkSurfaceElevated
import com.ainovel.audiobook.ui.theme.LiveGreen
import com.ainovel.audiobook.ui.theme.NeonCyan
import com.ainovel.audiobook.ui.theme.NeonCyanVariant
import com.ainovel.audiobook.ui.theme.NeonLavender
import com.ainovel.audiobook.ui.theme.NeonPurple
import com.ainovel.audiobook.ui.theme.TextPrimary
import com.ainovel.audiobook.ui.theme.TextSecondary
import com.ainovel.audiobook.ui.theme.TextTertiary

/**
 * Live Streaming Console Card faithfully mirroring the visual mockup in docs/mockups/live_console_studio_mockup.jpg.
 * Contains generation status, progress, token velocity, active model pill, and streaming console box.
 */
@Composable
fun StreamConsoleCard(
    chapterTitle: String,
    progressPercent: Int,
    tokensPerSecond: Int,
    activeModel: String,
    streamingText: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    // Blinking cursor animation
    val infiniteTransition = rememberInfiniteTransition(label = "BlinkingCursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CursorAlpha"
    )

    val consoleScrollState = rememberScrollState()

    // Auto-scroll when new text arrives
    LaunchedEffect(streamingText) {
        if (streamingText.isNotEmpty()) {
            consoleScrollState.animateScrollTo(consoleScrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Card with Radiant Gradient Border (Cyan to Purple)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(NeonCyan, NeonPurple)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .background(DarkSurfaceElevated)
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                // Chapter Title
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.wrapContentHeight()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Live Badge + Progress bar + percentage
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Live Pill Badge
                    Box(
                        modifier = Modifier
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isStreaming) NeonCyan else DarkSurfaceBorder)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isStreaming) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black)
                                )
                            }
                            Text(
                                text = stringResource(R.string.live_badge),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStreaming) Color.Black else TextSecondary
                                )
                            )
                        }
                    }

                    // Progress Track
                    LinearProgressIndicator(
                        progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = NeonCyan,
                        trackColor = DarkSurfaceBorder
                    )

                    // Percentage Text
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Velocity and Model Badge Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tokens_per_sec, tokensPerSecond),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )

                    // Active Model Pill Badge
                    Box(
                        modifier = Modifier
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, NeonPurple, RoundedCornerShape(16.dp))
                            .background(DarkSurface)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = activeModel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = NeonLavender
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Live Streaming Console Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.verticalGradient(
                    listOf(DarkSurfaceBorder, Color.Transparent)
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(18.dp)
            ) {
                // Header: Live streaming
                Text(
                    text = stringResource(R.string.live_streaming_header),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = NeonCyanVariant
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Streaming Content Viewport (Scrollable, never clips text)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 340.dp)
                        .verticalScroll(consoleScrollState)
                ) {
                    val displayCursor = if (isStreaming && cursorAlpha > 0.5f) "▋" else ""
                    Text(
                        text = if (streamingText.isEmpty()) "Waiting for AI generation..." else "$streamingText$displayCursor",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Default,
                            lineHeight = 22.sp,
                            color = if (streamingText.isEmpty()) TextTertiary else TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    )
                }
            }
        }
    }
}
