package com.ainovel.audiobook.data.local.dao

import androidx.room.*
import com.ainovel.audiobook.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterIndex ASC")
    fun getChaptersForNovel(novelId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY chapterIndex ASC")
    suspend fun getChaptersListForNovel(novelId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun getChapterById(id: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE novelId = :novelId AND chapterIndex = :index LIMIT 1")
    suspend fun getChapterByIndex(novelId: String, index: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("UPDATE chapters SET content = :content, wordCount = :wordCount, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateChapterContent(id: String, content: String, wordCount: Int, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chapters SET audioFilePath = :path, audioDurationMs = :durationMs, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateChapterAudio(id: String, path: String, durationMs: Long, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteChapter(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE novelId = :novelId")
    suspend fun deleteChaptersForNovel(novelId: String)
}
