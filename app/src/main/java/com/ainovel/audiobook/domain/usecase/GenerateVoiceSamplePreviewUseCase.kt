package com.ainovel.audiobook.domain.usecase

import com.ainovel.audiobook.tts.engine.VieNeuOnnxEngine
import com.ainovel.audiobook.tts.engine.VoiceSamplePreviewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase to generate or retrieve an instant auditory preview sample for a given voice.
 */
class GenerateVoiceSamplePreviewUseCase(
    private val previewManager: VoiceSamplePreviewManager
) {
    suspend operator fun invoke(
        voiceName: String,
        previewText: String? = null,
        localEngine: VieNeuOnnxEngine? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val text = previewText ?: previewManager.defaultBenchmarkText
            val wavBytes = previewManager.getOrGenerateSample(
                voiceName = voiceName,
                previewText = text,
                localEngine = localEngine
            )
            Result.success(wavBytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
