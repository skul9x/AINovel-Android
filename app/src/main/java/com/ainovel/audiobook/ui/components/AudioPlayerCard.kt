package com.ainovel.audiobook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ainovel.audiobook.R
import com.ainovel.audiobook.player.PlayerState
import com.ainovel.audiobook.ui.theme.DarkSurface
import com.ainovel.audiobook.ui.theme.DarkSurfaceBorder
import com.ainovel.audiobook.ui.theme.DarkSurfaceElevated
import com.ainovel.audiobook.ui.theme.NeonCyan
import com.ainovel.audiobook.ui.theme.NeonLavender
import com.ainovel.audiobook.ui.theme.NeonPurple
import com.ainovel.audiobook.ui.theme.TextPrimary
import com.ainovel.audiobook.ui.theme.TextSecondary
import com.ainovel.audiobook.ui.theme.TextTertiary

/**
 * Studio Audio Player Card faithfully mirroring the visual mockup.
 * Incorporates 40-bar dynamic waveform visualizer, scrubber, and precision controls.
 */
@Composable
fun AudioPlayerCard(
    playerState: PlayerState,
    waveformAmplitudes: FloatArray,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onRewind10s: () -> Unit,
    onForward10s: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeedChange: () -> Unit = {},
    playbackSpeed: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(DarkSurfaceBorder, DarkSurface)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // NOW PLAYING
            Text(
                text = stringResource(R.string.now_playing),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTertiary
                ),
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Novel Title
            Text(
                text = playerState.metadata.title.ifEmpty { "The Chronicles of Whisper" },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.wrapContentHeight()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 40-Bar Dynamic Animated Waveform
            WaveformVisualizer(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                amplitudes = waveformAmplitudes,
                progress = playerState.progress,
                isPlaying = playerState.isPlaying,
                canvasHeight = 72.dp
            )

            // Scrubber Slider for precision seeking
            Slider(
                value = playerState.progress,
                onValueChange = onSeekTo,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonPurple,
                    inactiveTrackColor = DarkSurfaceBorder
                )
            )

            // Time Readout: 02:14 / 08:37
            Text(
                text = playerState.formattedTimeDisplay,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = NeonLavender
                ),
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Chapter Title
            Text(
                text = playerState.metadata.chapterTitle.ifEmpty { "The Hidden Kingdom" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.wrapContentHeight()
            )

            // Author / Voice Timbre
            Text(
                text = "Lê Minh Thư · VieNeu Voice",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Control Row: SkipPrev, Rewind10s, Play/Pause, Forward10s, SkipNext
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onRewind10s) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Rewind 10s",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Main Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(NeonPurple, NeonLavender)
                            )
                        )
                        .clickable(onClick = onPlayPauseToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(onClick = onForward10s) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Forward 10s",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Controls: Repeat, Volume, Speed
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = TextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = TextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceBorder)
                        .clickable(onClick = onSpeedChange)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Speed",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${playbackSpeed}x",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        )
                    }
                }
            }
        }
    }
}
