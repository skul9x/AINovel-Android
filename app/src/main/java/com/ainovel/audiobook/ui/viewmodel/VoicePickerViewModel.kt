package com.ainovel.audiobook.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.audiobook.player.VoiceSamplePlayer
import com.ainovel.audiobook.tts.engine.VoicePreset
import com.ainovel.audiobook.tts.engine.VoicePresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoicePickerUiState(
    val voices: List<VoicePreset> = VoicePresets.getVoicePresets(),
    val selectedVoiceName: String = VoicePresets.defaultVoiceName,
    val currentlyPlayingVoiceId: String? = null,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null
)

class VoicePickerViewModel(
    private val voiceSamplePlayer: VoiceSamplePlayer? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoicePickerUiState())
    val uiState: StateFlow<VoicePickerUiState> = _uiState.asStateFlow()

    init {
        if (voiceSamplePlayer != null) {
            viewModelScope.launch {
                voiceSamplePlayer.state.collect { playerState ->
                    _uiState.update {
                        it.copy(
                            currentlyPlayingVoiceId = playerState.currentVoiceId,
                            isPlaying = playerState.isPlaying,
                            errorMessage = playerState.error
                        )
                    }
                }
            }
        }
    }

    fun selectVoice(voice: VoicePreset) {
        stopSample()
        _uiState.update { it.copy(selectedVoiceName = voice.name) }
    }

    fun previewSample(voiceName: String) {
        if (_uiState.value.currentlyPlayingVoiceId == voiceName && _uiState.value.isPlaying) {
            stopSample()
        } else {
            // Update state to playing for this voice
            _uiState.update {
                it.copy(
                    currentlyPlayingVoiceId = voiceName,
                    isPlaying = true,
                    errorMessage = null
                )
            }
        }
    }

    fun stopSample() {
        voiceSamplePlayer?.stopVoiceSample()
        _uiState.update {
            it.copy(
                currentlyPlayingVoiceId = null,
                isPlaying = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceSamplePlayer?.release()
    }
}
