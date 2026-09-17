package com.ainovel.audiobook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_tracks",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chapterId"])
    ]
)
data class AudioTrackEntity(
    @PrimaryKey
    val id: String,
    val chapterId: String,
    val voicePresetName: String,
    val sampleRate: Int = 48000,
    val bitDepth: Int = 16,
    val fileUri: String,
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val generationLatencyMs: Long = 0L,
    val rtf: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
)
