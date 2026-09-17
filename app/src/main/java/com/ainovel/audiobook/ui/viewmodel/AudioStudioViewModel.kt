package com.ainovel.audiobook.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.audiobook.player.AudioPlayerManager
import com.ainovel.audiobook.player.MediaMetadata
import com.ainovel.audiobook.player.PlaybackStatus
import com.ainovel.audiobook.player.PlayerState
import com.ainovel.audiobook.player.WaveformSampler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AudioStudioUiState(
    val playerState: PlayerState = PlayerState(
        status = PlaybackStatus.PLAYING,
        currentPositionMs = 134000L, // 02:14
        durationMs = 517000L,       // 08:37
        metadata = MediaMetadata(
            title = "The Chronicles of Whisper",
            chapterTitle = "The Hidden Kingdom",
            durationMs = 517000L
        )
    ),
    val waveformAmplitudes: FloatArray = FloatArray(40) { index ->
        // Default visualizer curve matching mockup
        val x = (index - 20).toFloat() / 10f
        (kotlin.math.exp(-x * x * 0.5f) * 0.85f + 0.15f).toFloat()
    },
    val selectedVoiceName: String = "Ngọc Huyền",
    val playbackSpeed: Float = 1.0f,
    val isVoicePickerOpen: Boolean = false,
    val isExporting: Boolean = false,
    val exportSuccessMessage: String? = null
)

class AudioStudioViewModel(
    private val audioPlayerManager: AudioPlayerManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioStudioUiState())
    val uiState: StateFlow<AudioStudioUiState> = _uiState.asStateFlow()

    init {
        if (audioPlayerManager != null) {
            viewModelScope.launch {
                audioPlayerManager.playerState.collect { state ->
                    _uiState.update { it.copy(playerState = state) }
                }
            }
        }
    }

    fun togglePlayPause() {
        if (audioPlayerManager != null) {
            if (_uiState.value.playerState.isPlaying) {
                audioPlayerManager.pause()
            } else {
                audioPlayerManager.play()
            }
        } else {
            // Local state toggle for standalone / preview
            val current = _uiState.value.playerState
            val newStatus = if (current.isPlaying) PlaybackStatus.PAUSED else PlaybackStatus.PLAYING
            _uiState.update { it.copy(playerState = current.copy(status = newStatus)) }
        }
    }

    fun seekTo(progressFraction: Float) {
        val duration = _uiState.value.playerState.durationMs
        val targetMs = (progressFraction * duration).toLong().coerceIn(0L, duration)
        if (audioPlayerManager != null) {
            audioPlayerManager.seekTo(targetMs)
        } else {
            _uiState.update {
                it.copy(playerState = it.playerState.copy(currentPositionMs = targetMs))
            }
        }
    }

    fun rewind10s() {
        val current = _uiState.value.playerState.currentPositionMs
        val newPos = (current - 10000L).coerceAtLeast(0L)
        seekTo(newPos.toFloat() / _uiState.value.playerState.durationMs.coerceAtLeast(1L))
    }

    fun forward10s() {
        val current = _uiState.value.playerState.currentPositionMs
        val duration = _uiState.value.playerState.durationMs
        val newPos = (current + 10000L).coerceAtMost(duration)
        seekTo(newPos.toFloat() / duration.coerceAtLeast(1L))
    }

    fun previousTrack() {
        seekTo(0f)
    }

    fun nextTrack() {
        // Next chapter or track
    }

    fun cycleSpeed() {
        val currentSpeed = _uiState.value.playbackSpeed
        val nextSpeed = when (currentSpeed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _uiState.update { it.copy(playbackSpeed = nextSpeed) }
    }

    fun openVoicePicker() {
        _uiState.update { it.copy(isVoicePickerOpen = true) }
    }

    fun closeVoicePicker() {
        _uiState.update { it.copy(isVoicePickerOpen = false) }
    }

    fun selectVoice(voiceName: String) {
        _uiState.update {
            it.copy(
                selectedVoiceName = voiceName,
                isVoicePickerOpen = false
            )
        }
    }

    fun updateWaveform(amplitudes: FloatArray) {
        val safeAmps = if (amplitudes.size == 40) amplitudes else WaveformSampler.sampleFloatPcm(amplitudes, 40)
        _uiState.update { it.copy(waveformAmplitudes = safeAmps) }
    }

    fun exportWav() {
        _uiState.update {
            it.copy(exportSuccessMessage = "WAV exported to Downloads/Audiobooks/track.wav")
        }
    }

    fun exportZip() {
        _uiState.update {
            it.copy(exportSuccessMessage = "Full Novel ZIP exported to Downloads/Audiobooks/novel.zip")
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportSuccessMessage = null) }
    }
}
