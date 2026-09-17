package com.ainovel.audiobook.data.repository

import com.ainovel.audiobook.data.local.dao.ChapterDao
import com.ainovel.audiobook.data.local.dao.NovelDao
import com.ainovel.audiobook.data.local.dao.OutlineDao
import com.ainovel.audiobook.data.local.entity.ChapterEntity
import com.ainovel.audiobook.data.local.entity.NovelEntity
import com.ainovel.audiobook.data.local.entity.OutlineEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface NovelRepository {
    fun getAllNovels(): Flow<List<NovelEntity>>
    suspend fun getNovel(id: String): NovelEntity?
    fun observeNovel(id: String): Flow<NovelEntity?>
    suspend fun createNovel(title: String, topic: String, genre: String, language: String = "vi"): NovelEntity
    suspend fun updateNovel(novel: NovelEntity)
    suspend fun deleteNovel(id: String)

    fun getChapters(novelId: String): Flow<List<ChapterEntity>>
    suspend fun getChaptersList(novelId: String): List<ChapterEntity>
    suspend fun getChapter(id: String): ChapterEntity?
    suspend fun saveChapter(chapter: ChapterEntity)
    suspend fun saveChapters(chapters: List<ChapterEntity>)
    suspend fun updateChapterContent(chapterId: String, content: String, wordCount: Int, status: String)
    suspend fun updateChapterAudio(chapterId: String, audioPath: String, durationMs: Long)

    fun observeOutline(novelId: String): Flow<OutlineEntity?>
    suspend fun getOutline(novelId: String): OutlineEntity?
    suspend fun saveOutline(novelId: String, rawText: String, titles: List<String>)
}

class NovelRepositoryImpl(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val outlineDao: OutlineDao
) : NovelRepository {

    override fun getAllNovels(): Flow<List<NovelEntity>> = novelDao.getAllNovels()

    override suspend fun getNovel(id: String): NovelEntity? = novelDao.getNovelById(id)

    override fun observeNovel(id: String): Flow<NovelEntity?> = novelDao.observeNovelById(id)

    override suspend fun createNovel(title: String, topic: String, genre: String, language: String): NovelEntity {
        val novel = NovelEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            topic = topic.trim(),
            genre = genre.trim(),
            language = language,
            status = "draft"
        )
        novelDao.insertOrUpdateNovel(novel)
        return novel
    }

    override suspend fun updateNovel(novel: NovelEntity) {
        novelDao.updateNovel(novel)
    }

    override suspend fun deleteNovel(id: String) {
        novelDao.deleteNovelById(id)
    }

    override fun getChapters(novelId: String): Flow<List<ChapterEntity>> =
        chapterDao.getChaptersForNovel(novelId)

    override suspend fun getChaptersList(novelId: String): List<ChapterEntity> =
        chapterDao.getChaptersListForNovel(novelId)

    override suspend fun getChapter(id: String): ChapterEntity? =
        chapterDao.getChapterById(id)

    override suspend fun saveChapter(chapter: ChapterEntity) {
        chapterDao.insertChapter(chapter)
    }

    override suspend fun saveChapters(chapters: List<ChapterEntity>) {
        chapterDao.insertChapters(chapters)
    }

    override suspend fun updateChapterContent(chapterId: String, content: String, wordCount: Int, status: String) {
        chapterDao.updateChapterContent(chapterId, content, wordCount, status)
    }

    override suspend fun updateChapterAudio(chapterId: String, audioPath: String, durationMs: Long) {
        chapterDao.updateChapterAudio(chapterId, audioPath, durationMs)
    }

    override fun observeOutline(novelId: String): Flow<OutlineEntity?> =
        outlineDao.observeOutline(novelId)

    override suspend fun getOutline(novelId: String): OutlineEntity? =
        outlineDao.getOutline(novelId)

    override suspend fun saveOutline(novelId: String, rawText: String, titles: List<String>) {
        val jsonArray = org.json.JSONArray()
        titles.forEach { jsonArray.put(it) }
        val outline = OutlineEntity(
            novelId = novelId,
            rawOutlineText = rawText,
            chapterTitlesJson = jsonArray.toString(),
            totalPlannedChapters = titles.size
        )
        outlineDao.insertOrUpdateOutline(outline)
    }
}
