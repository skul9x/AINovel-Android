package com.ainovel.audiobook.domain.model

data class RotationCandidate(
    val modelId: String,
    val rawApiKey: String,
    val keyHash: String,
    val maskedKey: String
)
