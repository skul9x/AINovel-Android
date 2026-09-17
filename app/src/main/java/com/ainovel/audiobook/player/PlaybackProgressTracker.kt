package com.ainovel.audiobook.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Synchronizes visualizer active bar highlighting and scrubber seek positions with player's elapsed time.
 * Also provides a reactive StateFlow progress tracker for UI observers.
 */
class PlaybackProgressTracker(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val barCount: Int = 40,
    private val pollingIntervalMs: Long = 50L
) {
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _activeBarIndex = MutableStateFlow(0)
    val activeBarIndex: StateFlow<Int> = _activeBarIndex.asStateFlow()

    private var trackingJob: Job? = null

    /**
     * Starts active tracking of position and duration provider.
     */
    fun startTracking(
        positionProvider: () -> Long,
        durationProvider: () -> Long,
        isPlayingProvider: () -> Boolean
    ) {
        stopTracking()
        trackingJob = scope.launch {
            while (isActive) {
                if (isPlayingProvider()) {
                    val pos = positionProvider()
                    val dur = durationProvider()
                    val prog = calculateProgress(pos, dur)
                    val bar = calculateActiveBarIndex(pos, dur, barCount)
                    _progress.value = prog
                    _activeBarIndex.value = bar
                }
                delay(pollingIntervalMs)
            }
        }
    }

    /**
     * Manually updates progress and active bar based on fixed position and duration.
     */
    fun update(currentPositionMs: Long, durationMs: Long) {
        _progress.value = calculateProgress(currentPositionMs, durationMs)
        _activeBarIndex.value = calculateActiveBarIndex(currentPositionMs, durationMs, barCount)
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    fun reset() {
        stopTracking()
        _progress.value = 0f
        _activeBarIndex.value = 0
    }

    companion object {
        /**
         * Calculates the active bar index (0 until barCount) corresponding to current playback position.
         */
        fun calculateActiveBarIndex(currentPositionMs: Long, durationMs: Long, barCount: Int): Int {
            if (durationMs <= 0L || barCount <= 0) return 0
            val progress = (currentPositionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
            val index = (progress * barCount).toInt()
            return index.coerceIn(0, barCount - 1)
        }

        /**
         * Calculates playback progress ratio strictly within [0.0f, 1.0f].
         */
        fun calculateProgress(currentPositionMs: Long, durationMs: Long): Float {
            if (durationMs <= 0L) return 0f
            return (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }

        /**
         * Computes target seek timestamp in milliseconds given a normalized scrub fraction [0.0f, 1.0f].
         */
        fun calculateSeekPosition(fraction: Float, durationMs: Long): Long {
            if (durationMs <= 0L) return 0L
            return (fraction.coerceIn(0f, 1f) * durationMs).toLong().coerceIn(0L, durationMs)
        }
    }
}
