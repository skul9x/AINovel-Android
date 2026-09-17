package com.ainovel.audiobook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["novelId"]),
        Index(value = ["novelId", "chapterIndex"], unique = true)
    ]
)
data class ChapterEntity(
    @PrimaryKey
    val id: String,
    val novelId: String,
    val chapterIndex: Int,
    val title: String,
    val content: String = "",
    val summary: String = "",
    val wordCount: Int = 0,
    val audioFilePath: String? = null,
    val audioDurationMs: Long = 0L,
    val status: String = "pending", // pending, generating, completed, error
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
