package com.ainovel.audiobook.domain.engine

import com.ainovel.audiobook.data.local.entity.ChapterEntity

class SlidingWindowContextManager(
    private val windowSize: Int = 3
) {

    /**
     * Builds the sliding window context from the chapter history.
     * Returns: (recentChaptersFullText, grandSummary)
     */
    fun assembleContext(allCompletedChapters: List<ChapterEntity>): Pair<String, String> {
        val sorted = allCompletedChapters
            .filter { it.content.isNotBlank() }
            .sortedBy { it.chapterIndex }

        if (sorted.isEmpty()) {
            return Pair("", "")
        }

        val recentChapters = sorted.takeLast(windowSize)
        val distantChapters = if (sorted.size > windowSize) {
            sorted.dropLast(windowSize)
        } else {
            emptyList()
        }

        // Combine full text of recent chapters
        val recentTextBuilder = StringBuilder()
        for (ch in recentChapters) {
            recentTextBuilder.append("--- CHƯƠNG ${ch.chapterIndex}: ${ch.title} ---\n")
            recentTextBuilder.append(ch.content.trim()).append("\n\n")
        }

        // Combine summaries of distant chapters
        val grandSummaryBuilder = StringBuilder()
        for (ch in distantChapters) {
            val summary = ch.summary.ifBlank {
                // Fallback: take first 200 chars if summary is missing
                ch.content.take(200) + "..."
            }
            grandSummaryBuilder.append("• Chương ${ch.chapterIndex} (${ch.title}): $summary\n")
        }

        return Pair(recentTextBuilder.toString().trim(), grandSummaryBuilder.toString().trim())
    }
}
