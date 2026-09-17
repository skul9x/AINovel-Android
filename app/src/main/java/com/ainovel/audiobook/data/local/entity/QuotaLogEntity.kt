package com.ainovel.audiobook.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quota_logs",
    indices = [
        Index(value = ["modelId", "keyHash"])
    ]
)
data class QuotaLogEntity(
    @PrimaryKey
    val id: String,
    val modelId: String,
    val keyHash: String,
    val errorStatus: Int = 429,
    val lockType: String = "rpm_cooldown", // rpm_cooldown, rpd_exhausted, timeout_cooldown
    val lockedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis(),
    val isPermanent: Boolean = false
)
