package com.ainovel.audiobook.player

import com.ainovel.audiobook.tts.storage.WavWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 40-column dynamic waveform sampler adapted from MakeAISound.
 * Downsamples raw 16-bit PCM, float PCM, or encoded WAV files into normalized
 * amplitude arrays ([0.0f, 1.0f]) for visualizer bars with memory caching.
 */
object WaveformSampler {

    const val DEFAULT_BAR_COUNT = 40
    private const val MAX_PCM16_VALUE = 32767.0

    private val waveformCache = ConcurrentHashMap<String, FloatArray>()

    /**
     * Downsamples a 16-bit PCM [ShortArray] into [barCount] normalized amplitude bars.
     */
    fun samplePcm16(
        pcm: ShortArray,
        barCount: Int = DEFAULT_BAR_COUNT,
        useRms: Boolean = true,
        normalizeToPeak: Boolean = true
    ): FloatArray {
        if (barCount <= 0) return FloatArray(0)
        if (pcm.isEmpty()) return FloatArray(barCount) { 0f }

        val amplitudes = FloatArray(barCount)
        val samplesPerBar = pcm.size.toDouble() / barCount.toDouble()

        for (i in 0 until barCount) {
            val startIndex = (i * samplesPerBar).toInt().coerceIn(0, pcm.size - 1)
            val endIndex = ((i + 1) * samplesPerBar).toInt().coerceIn(startIndex + 1, pcm.size)
            val count = endIndex - startIndex

            if (count <= 0) {
                amplitudes[i] = 0f
                continue
            }

            if (useRms) {
                var sumSquares = 0.0
                for (j in startIndex until endIndex) {
                    val normalized = abs(pcm[j].toDouble()) / MAX_PCM16_VALUE
                    sumSquares += normalized * normalized
                }
                amplitudes[i] = sqrt(sumSquares / count.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                var peak = 0.0
                for (j in startIndex until endIndex) {
                    val normalized = abs(pcm[j].toDouble()) / MAX_PCM16_VALUE
                    if (normalized > peak) {
                        peak = normalized
                    }
                }
                amplitudes[i] = peak.toFloat().coerceIn(0f, 1f)
            }
        }

        return applyPeakNormalization(amplitudes, normalizeToPeak)
    }

    /**
     * Downsamples normalized [-1.0f, 1.0f] [FloatArray] PCM into [barCount] normalized amplitude bars.
     */
    fun sampleFloatPcm(
        pcm: FloatArray,
        barCount: Int = DEFAULT_BAR_COUNT,
        useRms: Boolean = true,
        normalizeToPeak: Boolean = true
    ): FloatArray {
        if (barCount <= 0) return FloatArray(0)
        if (pcm.isEmpty()) return FloatArray(barCount) { 0f }

        val amplitudes = FloatArray(barCount)
        val samplesPerBar = pcm.size.toDouble() / barCount.toDouble()

        for (i in 0 until barCount) {
            val startIndex = (i * samplesPerBar).toInt().coerceIn(0, pcm.size - 1)
            val endIndex = ((i + 1) * samplesPerBar).toInt().coerceIn(startIndex + 1, pcm.size)
            val count = endIndex - startIndex

            if (count <= 0) {
                amplitudes[i] = 0f
                continue
            }

            if (useRms) {
                var sumSquares = 0.0
                for (j in startIndex until endIndex) {
                    val normalized = abs(pcm[j].toDouble()).coerceIn(0.0, 1.0)
                    sumSquares += normalized * normalized
                }
                amplitudes[i] = sqrt(sumSquares / count.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                var peak = 0.0
                for (j in startIndex until endIndex) {
                    val normalized = abs(pcm[j].toDouble()).coerceIn(0.0, 1.0)
                    if (normalized > peak) {
                        peak = normalized
                    }
                }
                amplitudes[i] = peak.toFloat().coerceIn(0f, 1f)
            }
        }

        return applyPeakNormalization(amplitudes, normalizeToPeak)
    }

    /**
     * Extracts PCM samples from encoded WAV bytes and downsamples them into [barCount] amplitude bars.
     */
    fun sampleWavBytes(
        wavBytes: ByteArray,
        barCount: Int = DEFAULT_BAR_COUNT,
        useRms: Boolean = true,
        normalizeToPeak: Boolean = true
    ): FloatArray {
        if (wavBytes.isEmpty() || barCount <= 0) return FloatArray(barCount.coerceAtLeast(0)) { 0f }
        return try {
            val parsedWav = WavWriter.readWav(wavBytes)
            samplePcm16(parsedWav.pcm16, barCount, useRms, normalizeToPeak)
        } catch (_: Throwable) {
            FloatArray(barCount) { 0f }
        }
    }

    /**
     * Reads a WAV file from disk and computes its visualizer amplitude bars.
     */
    fun sampleWavFile(
        file: File,
        barCount: Int = DEFAULT_BAR_COUNT,
        useRms: Boolean = true,
        normalizeToPeak: Boolean = true
    ): FloatArray {
        if (!file.exists() || !file.canRead() || barCount <= 0) return FloatArray(barCount.coerceAtLeast(0)) { 0f }
        val cacheKey = "${file.absolutePath}_${file.lastModified()}_$barCount"
        waveformCache[cacheKey]?.let { return it }

        return try {
            val parsedWav = WavWriter.readWav(file)
            val result = samplePcm16(parsedWav.pcm16, barCount, useRms, normalizeToPeak)
            waveformCache[cacheKey] = result
            result
        } catch (_: Throwable) {
            FloatArray(barCount) { 0f }
        }
    }

    /**
     * Retrieves or computes cached waveform.
     */
    fun getOrCompute(cacheKey: String, compute: () -> FloatArray): FloatArray {
        return waveformCache.computeIfAbsent(cacheKey) { compute() }
    }

    fun cacheWaveform(key: String, waveform: FloatArray) {
        waveformCache[key] = waveform
    }

    fun getFromCache(key: String): FloatArray? = waveformCache[key]

    fun clearCache() {
        waveformCache.clear()
    }

    private fun applyPeakNormalization(amplitudes: FloatArray, normalizeToPeak: Boolean): FloatArray {
        if (!normalizeToPeak || amplitudes.isEmpty()) {
            return amplitudes
        }

        var maxVal = 0f
        for (v in amplitudes) {
            if (v > maxVal) maxVal = v
        }

        if (maxVal > 1e-5f) {
            val scale = 1f / maxVal
            for (i in amplitudes.indices) {
                amplitudes[i] = (amplitudes[i] * scale).coerceIn(0f, 1f)
            }
        }

        return amplitudes
    }
}
