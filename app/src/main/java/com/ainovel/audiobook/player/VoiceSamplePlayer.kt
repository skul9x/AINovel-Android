package com.ainovel.audiobook.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

/**
 * State representing preview voice sample playback in Voice Picker Studio.
 */
data class VoicePlayerState(
    val currentVoiceId: String? = null,
    val isPlaying: Boolean = false,
    val error: String? = null
)

/**
 * Independent, lightweight audio player dedicated to instant sound sample preview
 * inside the Voice Picker Studio.
 *
 * Automatically stops previous sample when a new voice is selected without
 * affecting or clearing the main novel's [AudioPlayerManager] playback state.
 */
class VoiceSamplePlayer(
    private val playerAdapter: MediaPlayerAdapter = AndroidMediaPlayerAdapter()
) {
    private val _state = MutableStateFlow(VoicePlayerState())
    val state: StateFlow<VoicePlayerState> = _state.asStateFlow()

    init {
        setupListeners()
    }

    private fun setupListeners() {
        playerAdapter.setOnCompletionListener {
            _state.value = VoicePlayerState(currentVoiceId = null, isPlaying = false)
        }

        playerAdapter.setOnErrorListener { what, extra ->
            _state.value = VoicePlayerState(
                currentVoiceId = null,
                isPlaying = false,
                error = "Voice sample playback error ($what, $extra)"
            )
            true
        }
    }

    /**
     * Plays a voice sample preview file for [voiceId].
     * If another sample is currently playing, it is stopped immediately.
     */
    fun playVoiceSample(voiceId: String, audioPath: String): Boolean {
        return try {
            // Stop previously playing sample first
            stopVoiceSample()

            playerAdapter.reset()
            setupListeners()
            playerAdapter.setDataSource(audioPath)
            playerAdapter.prepare()
            playerAdapter.start()

            _state.value = VoicePlayerState(
                currentVoiceId = voiceId,
                isPlaying = true,
                error = null
            )
            true
        } catch (e: Throwable) {
            _state.value = VoicePlayerState(
                currentVoiceId = voiceId,
                isPlaying = false,
                error = e.message ?: "Failed to play voice sample"
            )
            false
        }
    }

    fun playVoiceSample(voiceId: String, audioFile: File): Boolean =
        playVoiceSample(voiceId, audioFile.absolutePath)

    /**
     * Convenience method to play in-memory WAV bytes by writing to a temporary file.
     */
    fun playVoiceBytes(voiceId: String, wavBytes: ByteArray, cacheDir: File): Boolean {
        return try {
            val tempFile = File(cacheDir, "sample_preview_${voiceId.replace('/', '_')}.wav")
            FileOutputStream(tempFile).use { it.write(wavBytes) }
            playVoiceSample(voiceId, tempFile)
        } catch (e: Throwable) {
            _state.value = VoicePlayerState(
                currentVoiceId = voiceId,
                isPlaying = false,
                error = e.message
            )
            false
        }
    }

    /**
     * Stops current voice preview playback.
     */
    fun stopVoiceSample() {
        try {
            if (playerAdapter.isPlaying()) {
                playerAdapter.stop()
            }
        } catch (_: Throwable) {}
        _state.value = VoicePlayerState(currentVoiceId = null, isPlaying = false)
    }

    /**
     * Releases player resources.
     */
    fun release() {
        stopVoiceSample()
        try {
            playerAdapter.release()
        } catch (_: Throwable) {}
        _state.value = VoicePlayerState()
    }
}
