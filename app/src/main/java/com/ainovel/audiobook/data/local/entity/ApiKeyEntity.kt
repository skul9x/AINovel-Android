package com.ainovel.audiobook.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "api_keys",
    indices = [
        Index(value = ["keyHash"], unique = true)
    ]
)
data class ApiKeyEntity(
    @PrimaryKey
    val id: String,
    val maskedKey: String,
    val keyHash: String,
    val provider: String = "gemini", // "gemini", "openai_compatible"
    val label: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
