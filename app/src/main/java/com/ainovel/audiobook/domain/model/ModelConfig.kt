package com.ainovel.audiobook.domain.model

data class ModelConfig(
    val id: String,
    val priority: Int,
    val isFallbackOnly: Boolean = false,
    val timeoutMs: Long = 60_000L,
    val rpmCooldownMs: Long = 30_000L,
    val rpdExhaustedMs: Long = 108_000_000L, // 30 hours
    val timeoutCooldownMs: Long = 300_000L // 5 minutes
) {
    companion object {
        val DEFAULT_MODELS = listOf(
            ModelConfig(id = "gemini-2.5-flash", priority = 1),
            ModelConfig(id = "gemini-2.0-flash", priority = 2),
            ModelConfig(id = "gemini-1.5-flash", priority = 3),
            ModelConfig(id = "gemini-1.5-pro", priority = 4, isFallbackOnly = true),
            ModelConfig(id = "gemini-3.7-flash", priority = 5),
            ModelConfig(id = "gemini-3.5-flash-lite", priority = 6)
        )

        fun normalizeModelId(rawId: String): String {
            return rawId.removePrefix("models/").trim()
        }
    }
}
