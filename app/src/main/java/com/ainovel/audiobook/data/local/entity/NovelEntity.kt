package com.ainovel.audiobook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val topic: String,
    val genre: String,
    val language: String = "vi", // "vi" or "en"
    val totalChapters: Int = 0,
    val completedChapters: Int = 0,
    val status: String = "draft", // draft, generating, completed, paused
    val grandSummary: String = "",
    val worldLore: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
