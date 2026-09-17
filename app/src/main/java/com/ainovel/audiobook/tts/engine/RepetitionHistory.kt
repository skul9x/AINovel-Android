package com.ainovel.audiobook.tts.engine

/**
 * Sliding-window repetition-penalty history for VieNeu-TTS v3 Turbo.
 * Fixed-size FIFO sliding window (default 64 frames).
 */
class ChannelWindow(val window: Int = DEFAULT_REP_WINDOW) {
    private val seen = HashMap<Int, Int>()
    private val order = ArrayDeque<Int>()

    fun add(code: Int) {
        seen[code] = (seen[code] ?: 0) + 1
        if (window > 0) {
            order.addLast(code)
            if (order.size > window) {
                val old = order.removeFirst()
                val count = seen[old] ?: 0
                if (count <= 1) {
                    seen.remove(old)
                } else {
                    seen[old] = count - 1
                }
            }
        }
    }

    fun contains(code: Int): Boolean = seen.containsKey(code)

    fun getCodes(): Set<Int> = seen.keys

    val uniqueCount: Int get() = seen.size

    val windowCount: Int get() = order.size

    fun getCount(code: Int): Int = seen[code] ?: 0

    fun clear() {
        seen.clear()
        order.clear()
    }

    companion object {
        const val DEFAULT_REP_WINDOW: Int = 64
    }
}

/**
 * Multi-channel repetition penalty history.
 */
class RepetitionHistory(
    val nChannels: Int = 16,
    val window: Int = ChannelWindow.DEFAULT_REP_WINDOW
) {
    val channels: Array<ChannelWindow> = Array(nChannels) { ChannelWindow(window) }

    operator fun get(ch: Int): ChannelWindow = channels[ch]

    val size: Int get() = channels.size

    fun clear() {
        for (ch in channels) {
            ch.clear()
        }
    }

    companion object {
        const val DEFAULT_REP_WINDOW: Int = 64
    }
}
