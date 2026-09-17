package com.ainovel.audiobook.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ainovel.audiobook.R
import com.ainovel.audiobook.tts.engine.VoicePreset
import com.ainovel.audiobook.tts.engine.VoicePresets
import com.ainovel.audiobook.ui.theme.DarkBackground
import com.ainovel.audiobook.ui.theme.DarkSurface
import com.ainovel.audiobook.ui.theme.DarkSurfaceBorder
import com.ainovel.audiobook.ui.theme.DarkSurfaceElevated
import com.ainovel.audiobook.ui.theme.NeonCyan
import com.ainovel.audiobook.ui.theme.NeonLavender
import com.ainovel.audiobook.ui.theme.NeonPurple
import com.ainovel.audiobook.ui.theme.NeonPurpleVariant
import com.ainovel.audiobook.ui.theme.TextPrimary
import com.ainovel.audiobook.ui.theme.TextSecondary
import com.ainovel.audiobook.ui.theme.TextTertiary
import kotlin.math.sin

/**
 * Animated micro-waveform indicator (4 bars) embedded directly into sample preview buttons.
 */
@Composable
fun MicroWaveformIndicator(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    color: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "MicroWaveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MicroWavePhase"
    )

    Canvas(
        modifier = modifier
            .size(width = 18.dp, height = 16.dp)
            .padding(horizontal = 2.dp)
    ) {
        val barCount = 4
        val slotWidth = size.width / barCount
        val barWidth = slotWidth * 0.6f
        val maxHeight = size.height
        val minHeight = 4.dp.toPx()

        for (i in 0 until barCount) {
            val factor = if (isPlaying) {
                (sin(phase + i * 1.2f).toFloat() * 0.4f + 0.6f).coerceIn(0.2f, 1f)
            } else {
                0.3f + (i % 2) * 0.2f
            }

            val barHeight = (factor * (maxHeight - minHeight) + minHeight)
            val left = i * slotWidth + (slotWidth - barWidth) / 2f
            val top = (maxHeight - barHeight) / 2f

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * Voice Picker Studio Bottom Sheet.
 * Displays all available VieNeu-TTS voices with instant sample preview, grouping, and search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePickerBottomSheet(
    onDismissRequest: () -> Unit,
    onVoiceSelected: (VoicePreset) -> Unit,
    onPreviewSample: (String) -> Unit,
    onStopSample: () -> Unit,
    currentlyPlayingVoiceId: String? = null,
    selectedVoiceName: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    allVoices: List<VoicePreset> = VoicePresets.getVoicePresets()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedRegion by remember { mutableStateOf<String?>(null) } // null = All, Bắc, Trung, Nam
    var selectedGender by remember { mutableStateOf<String?>(null) } // null = All, Nữ, Nam

    val regions = listOf("Tất cả", "Bắc", "Trung", "Nam")
    val genders = listOf("Tất cả", "Nữ", "Nam")

    val filteredVoices = remember(searchQuery, selectedRegion, selectedGender, allVoices) {
        allVoices.filter { preset ->
            val matchesSearch = searchQuery.isBlank() ||
                    preset.name.contains(searchQuery, ignoreCase = true) ||
                    preset.description.contains(searchQuery, ignoreCase = true)

            val matchesRegion = selectedRegion == null || selectedRegion == "Tất cả" ||
                    preset.region.contains(selectedRegion!!, ignoreCase = true) ||
                    preset.description.contains(selectedRegion!!, ignoreCase = true)

            val matchesGender = selectedGender == null || selectedGender == "Tất cả" ||
                    preset.genderDisplay.equals(selectedGender, ignoreCase = true) ||
                    preset.gender.contains(selectedGender!!, ignoreCase = true)

            matchesSearch && matchesRegion && matchesGender
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onStopSample()
            onDismissRequest()
        },
        sheetState = sheetState,
        containerColor = DarkBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NeonLavender.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = stringResource(R.string.voice_picker_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonLavender
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.select_voice),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    onStopSample()
                    onDismissRequest()
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_voice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = NeonCyan
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = DarkSurfaceBorder,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter chips row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(regions) { region ->
                    val isSelected = (selectedRegion == null && region == "Tất cả") || selectedRegion == region
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedRegion = if (region == "Tất cả") null else region
                        },
                        label = {
                            Text(
                                text = region,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonPurple,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceElevated,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) NeonCyan else DarkSurfaceBorder
                        )
                    )
                }

                items(genders.filter { it != "Tất cả" }) { gender ->
                    val isSelected = selectedGender == gender
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedGender = if (isSelected) null else gender
                        },
                        label = {
                            Text(
                                text = gender,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonPurpleVariant,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceElevated,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) NeonCyan else DarkSurfaceBorder
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Voice List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredVoices, key = { it.name }) { preset ->
                    val isCurrentlySelected = selectedVoiceName == preset.name
                    val isPlayingThis = currentlyPlayingVoiceId == preset.name

                    VoicePresetCard(
                        preset = preset,
                        isSelected = isCurrentlySelected,
                        isPlaying = isPlayingThis,
                        onSelect = { onVoiceSelected(preset) },
                        onTogglePreview = {
                            if (isPlayingThis) {
                                onStopSample()
                            } else {
                                onPreviewSample(preset.name)
                            }
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Individual voice preset card matching mockup layout.
 */
@Composable
fun VoicePresetCard(
    preset: VoicePreset,
    isSelected: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onTogglePreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSelected -> NeonCyan
        isPlaying -> NeonPurple
        else -> DarkSurfaceBorder
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkSurfaceElevated else DarkSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(borderColor))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(14.dp)
        ) {
            // Title: Name + Style
            Text(
                text = preset.getFormattedDisplayName(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Chips & Preview button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Instant Preview Button with Animated Micro Waveform
                Box(
                    modifier = Modifier
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isPlaying) NeonCyan else NeonPurple)
                        .clickable(onClick = onTogglePreview)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isPlaying) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = stringResource(R.string.stop_sample),
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            MicroWaveformIndicator(isPlaying = false, color = Color.White)
                        }

                        Text(
                            text = if (isPlaying) stringResource(R.string.stop_sample) else stringResource(R.string.preview_sample),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isPlaying) Color.Black else Color.White
                            )
                        )
                    }
                }

                // Region Tag
                if (preset.region.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceBorder)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = preset.region,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                    }
                }

                // Gender Tag
                if (preset.genderDisplay.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceBorder)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = preset.genderDisplay,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                    }
                }
            }

            // Description
            if (preset.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextTertiary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
