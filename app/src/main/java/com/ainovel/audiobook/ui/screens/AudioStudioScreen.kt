package com.ainovel.audiobook.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ainovel.audiobook.R
import com.ainovel.audiobook.ui.components.AudioPlayerCard
import com.ainovel.audiobook.ui.components.VoicePickerBottomSheet
import com.ainovel.audiobook.ui.theme.DarkBackground
import com.ainovel.audiobook.ui.theme.DarkSurface
import com.ainovel.audiobook.ui.theme.DarkSurfaceBorder
import com.ainovel.audiobook.ui.theme.DarkSurfaceElevated
import com.ainovel.audiobook.ui.theme.NeonCyan
import com.ainovel.audiobook.ui.theme.NeonLavender
import com.ainovel.audiobook.ui.theme.NeonPurple
import com.ainovel.audiobook.ui.theme.TextPrimary
import com.ainovel.audiobook.ui.theme.TextSecondary
import com.ainovel.audiobook.ui.viewmodel.AudioStudioViewModel
import com.ainovel.audiobook.ui.viewmodel.VoicePickerViewModel

/**
 * Audio Studio Screen: Complete player UI containing 40-bar WaveformVisualizer,
 * precision scrubber, voice picker trigger, and Export WAV/ZIP actions.
 * Pixel-by-pixel faithful to voice_picker_studio_mockup.jpg.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioStudioScreen(
    studioViewModel: AudioStudioViewModel,
    voicePickerViewModel: VoicePickerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val studioState by studioViewModel.uiState.collectAsState()
    val voiceState by voicePickerViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(studioState.exportSuccessMessage) {
        studioState.exportSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            studioViewModel.clearExportMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.audio_studio_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Audio Player Card (40-bar Waveform, Scrubber, Transport Controls)
            item {
                AudioPlayerCard(
                    playerState = studioState.playerState,
                    waveformAmplitudes = studioState.waveformAmplitudes,
                    onPlayPauseToggle = { studioViewModel.togglePlayPause() },
                    onSeekTo = { studioViewModel.seekTo(it) },
                    onRewind10s = { studioViewModel.rewind10s() },
                    onForward10s = { studioViewModel.forward10s() },
                    onPrevious = { studioViewModel.previousTrack() },
                    onNext = { studioViewModel.nextTrack() },
                    onSpeedChange = { studioViewModel.cycleSpeed() },
                    playbackSpeed = studioState.playbackSpeed
                )
            }

            // Voice Selector Trigger Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = stringResource(R.string.voice_picker_title),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NeonLavender
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Giọng đọc hiện tại: ${studioState.selectedVoiceName}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { studioViewModel.openVoicePicker() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonPurple,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.select_voice),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Export Actions (WAV & ZIP)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { studioViewModel.exportWav() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentHeight()
                            .border(1.dp, NeonCyan, RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = stringResource(R.string.export_wav),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = NeonCyan
                            )
                        )
                    }

                    Button(
                        onClick = { studioViewModel.exportZip() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurfaceElevated,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentHeight()
                            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = NeonLavender,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = stringResource(R.string.export_zip),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }

        // Voice Picker Studio Bottom Sheet
        if (studioState.isVoicePickerOpen) {
            VoicePickerBottomSheet(
                onDismissRequest = { studioViewModel.closeVoicePicker() },
                onVoiceSelected = { preset ->
                    studioViewModel.selectVoice(preset.name)
                    voicePickerViewModel.selectVoice(preset)
                },
                onPreviewSample = { voiceName ->
                    voicePickerViewModel.previewSample(voiceName)
                },
                onStopSample = {
                    voicePickerViewModel.stopSample()
                },
                currentlyPlayingVoiceId = voiceState.currentlyPlayingVoiceId,
                selectedVoiceName = studioState.selectedVoiceName
            )
        }
    }
}
