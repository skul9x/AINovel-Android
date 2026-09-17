package com.ainovel.audiobook.domain.model

sealed class StreamEvent {
    data class Token(val text: String, val modelId: String) : StreamEvent()
    data class Completed(val fullText: String, val totalTokens: Int, val durationMs: Long) : StreamEvent()
    data class Error(val code: Int, val message: String, val retryable: Boolean) : StreamEvent()
}
