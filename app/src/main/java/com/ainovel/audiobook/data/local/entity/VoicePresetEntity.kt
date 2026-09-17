package com.ainovel.audiobook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_presets")
data class VoicePresetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val gender: String = "Nữ", // Nữ / Nam
    val region: String = "Bắc", // Bắc, Trung, Nam
    val style: String = "tu_nhien", // tu_nhien, ke_chuyen, tin_tuc
    val sampleAudioUri: String? = null,
    val speakerEmbeddingJson: String? = null,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
