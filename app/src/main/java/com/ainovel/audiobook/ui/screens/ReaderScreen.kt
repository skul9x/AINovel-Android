package com.ainovel.audiobook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ainovel.audiobook.R
import com.ainovel.audiobook.ui.theme.DarkSurfaceBorder
import com.ainovel.audiobook.ui.theme.NeonCyan
import com.ainovel.audiobook.ui.theme.NeonLavender
import com.ainovel.audiobook.ui.theme.NeonPurple
import com.ainovel.audiobook.ui.theme.ReaderDayBg
import com.ainovel.audiobook.ui.theme.ReaderDayText
import com.ainovel.audiobook.ui.theme.ReaderNightBg
import com.ainovel.audiobook.ui.theme.ReaderNightText
import com.ainovel.audiobook.ui.theme.ReaderSepiaBg
import com.ainovel.audiobook.ui.theme.ReaderSepiaText
import com.ainovel.audiobook.ui.theme.TextPrimary
import com.ainovel.audiobook.ui.theme.TextSecondary
import com.ainovel.audiobook.ui.viewmodel.ReaderThemeMode
import com.ainovel.audiobook.ui.viewmodel.ReaderViewModel

/**
 * Distraction-free novel reader with adjustable font size, line spacing,
 * Day/Night/Sepia themes, and "Generate Audiobook" trigger button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAudioStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTypographyControls by remember { mutableStateOf(false) }

    val (screenBg, contentTextColor) = when (uiState.themeMode) {
        ReaderThemeMode.DAY -> Pair(ReaderDayBg, ReaderDayText)
        ReaderThemeMode.NIGHT -> Pair(ReaderNightBg, ReaderNightText)
        ReaderThemeMode.SEPIA -> Pair(ReaderSepiaBg, ReaderSepiaText)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = screenBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.chapterTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = contentTextColor
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
                            tint = contentTextColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showTypographyControls = !showTypographyControls }) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Typography Settings",
                            tint = contentTextColor
                        )
                    }

                    IconButton(onClick = {
                        viewModel.triggerAudiobookGeneration {
                            onNavigateToAudioStudio()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = stringResource(R.string.generate_audiobook),
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = screenBg)
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(screenBg)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        viewModel.triggerAudiobookGeneration {
                            onNavigateToAudioStudio()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.generate_audiobook),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Collapsible Typography and Theme Inspector
            if (showTypographyControls) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x22888888))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(14.dp)
                        ) {
                            // Font Size Slider
                            Text(
                                text = "${stringResource(R.string.font_size)}: ${uiState.fontSizeSp.toInt()} sp",
                                style = MaterialTheme.typography.labelSmall.copy(color = contentTextColor)
                            )
                            Slider(
                                value = uiState.fontSizeSp,
                                onValueChange = { viewModel.updateFontSize(it) },
                                valueRange = 12f..26f,
                                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                            )

                            // Theme Selector (Day, Night, Sepia)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.themeMode == ReaderThemeMode.DAY,
                                    onClick = { viewModel.setThemeMode(ReaderThemeMode.DAY) },
                                    label = { Text(stringResource(R.string.theme_day)) }
                                )
                                FilterChip(
                                    selected = uiState.themeMode == ReaderThemeMode.NIGHT,
                                    onClick = { viewModel.setThemeMode(ReaderThemeMode.NIGHT) },
                                    label = { Text(stringResource(R.string.theme_night)) }
                                )
                                FilterChip(
                                    selected = uiState.themeMode == ReaderThemeMode.SEPIA,
                                    onClick = { viewModel.setThemeMode(ReaderThemeMode.SEPIA) },
                                    label = { Text(stringResource(R.string.theme_sepia)) }
                                )
                            }
                        }
                    }
                }
            }

            // Novel Title & Chapter Title
            item {
                Text(
                    text = uiState.novelTitle,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = contentTextColor.copy(alpha = 0.65f),
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.wrapContentHeight()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.chapterTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = contentTextColor,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.wrapContentHeight()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Novel Body text with Zero Text Clipping & Dynamic Font Metrics
            item {
                val calculatedLineHeight = (uiState.fontSizeSp * uiState.lineSpacingMultiplier).sp
                Text(
                    text = uiState.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = uiState.fontSizeSp.sp,
                        lineHeight = calculatedLineHeight,
                        color = contentTextColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                )
            }
        }
    }
}
