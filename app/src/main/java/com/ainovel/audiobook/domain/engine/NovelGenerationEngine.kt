package com.ainovel.audiobook.domain.engine

import android.util.Log
import com.ainovel.audiobook.data.local.entity.ChapterEntity
import com.ainovel.audiobook.data.local.entity.OutlineEntity
import com.ainovel.audiobook.data.remote.OkHttpLlmClient
import com.ainovel.audiobook.data.remote.PromptTemplateService
import com.ainovel.audiobook.data.repository.NovelRepository
import com.ainovel.audiobook.domain.model.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class NovelGenerationEngine(
    private val rotationManager: RotationManager,
    private val llmClient: OkHttpLlmClient,
    private val promptService: PromptTemplateService = PromptTemplateService(),
    private val contextManager: SlidingWindowContextManager = SlidingWindowContextManager(),
    private val novelRepository: NovelRepository,
    var customEndpointUrl: String? = null
) {

    /**
     * Generates outline for a novel, parses chapter titles, and initializes pending chapters.
     */
    suspend fun generateOutline(
        novelId: String,
        topic: String,
        genre: String,
        totalChapters: Int,
        language: String = "vi"
    ): Result<OutlineEntity> = withContext(Dispatchers.IO) {
        val (sysPrompt, userPrompt) = promptService.buildOutlinePrompt(
            topic = topic,
            genre = genre,
            totalChapters = totalChapters,
            language = language
        )

        val candidate = rotationManager.electCandidate()
            ?: return@withContext Result.failure(Exception("Không tìm thấy Google API Key nào khả dụng. Vui lòng vào Cài đặt để thêm key."))

        Log.d("AINovel_Engine", "Generating outline: novelId=$novelId, model=${candidate.modelId}, totalChapters=$totalChapters")

        val endpoint = customEndpointUrl ?: llmClient.defaultBaseUrl
        val outlineTextResult = llmClient.chat(candidate, sysPrompt, userPrompt, endpoint)

        return@withContext if (outlineTextResult.isSuccess) {
            val rawOutline = outlineTextResult.getOrNull() ?: ""
            val extractedTitles = promptService.extractChapterTitles(rawOutline)
            Log.d("AINovel_Engine", "Extracted ${extractedTitles.size} chapters from LLM outline response")

            // Save outline
            novelRepository.saveOutline(novelId, rawOutline, extractedTitles)

            // Create initial chapters
            val chapters = extractedTitles.mapIndexed { index, title ->
                ChapterEntity(
                    id = UUID.randomUUID().toString(),
                    novelId = novelId,
                    chapterIndex = index + 1,
                    title = title,
                    status = "pending"
                )
            }
            novelRepository.saveChapters(chapters)

            val savedOutline = novelRepository.getOutline(novelId)
                ?: OutlineEntity(novelId, rawOutline, "[]", extractedTitles.size)

            Result.success(savedOutline)
        } else {
            val ex = outlineTextResult.exceptionOrNull()
            Log.e("AINovel_Engine", "Outline generation failed: ${ex?.message}", ex)
            if (ex?.message?.contains("429") == true) {
                rotationManager.reportRateLimit(candidate.modelId, candidate.keyHash, ex.message)
            } else if (ex?.message?.contains("503") == true) {
                rotationManager.reportServerOverloadOrTimeout(candidate.modelId, candidate.keyHash)
            }
            Result.failure(ex ?: Exception("Failed to generate outline"))
        }
    }

    /**
     * Generates a single chapter using sliding-window context and streams tokens back to the caller.
     */
    suspend fun generateChapter(
        novelId: String,
        chapterIndex: Int,
        onToken: ((String) -> Unit)? = null
    ): Result<ChapterEntity> = withContext(Dispatchers.IO) {
        val novel = novelRepository.getNovel(novelId)
            ?: return@withContext Result.failure(Exception("Novel not found with id $novelId"))

        val allChapters = novelRepository.getChaptersList(novelId)
        val targetChapter = allChapters.find { it.chapterIndex == chapterIndex }
            ?: return@withContext Result.failure(Exception("Chapter $chapterIndex not found for novel $novelId"))

        val completedChapters = allChapters.filter { it.chapterIndex < chapterIndex && it.content.isNotBlank() }
        val (recentText, grandSummary) = contextManager.assembleContext(completedChapters)

        val (sysPrompt, userPrompt) = promptService.buildChapterPrompt(
            novelTitle = novel.title,
            genre = novel.genre,
            chapterIndex = chapterIndex,
            chapterTitle = targetChapter.title,
            chapterObjective = targetChapter.summary.ifBlank { "Diễn tiến theo nhịp độ mạch truyện." },
            recentChaptersText = recentText,
            grandSummary = grandSummary,
            worldLore = novel.worldLore,
            language = novel.language
        )

        // Try with elected candidates (retry on failure)
        var maxAttempts = 3
        var lastError: Throwable? = null

        while (maxAttempts-- > 0) {
            val candidate = rotationManager.electCandidate()
                ?: return@withContext Result.failure(Exception("Không có candidate API Key / Model khả dụng"))

            Log.d("AINovel_Engine", "Generating chapter $chapterIndex with candidate: ${candidate.modelId}")

            val endpoint = customEndpointUrl ?: llmClient.defaultBaseUrl
            var tokensReceived = 0
            val fullTextBuilder = StringBuilder()
            var failureEncountered: StreamEvent.Error? = null

            llmClient.chatStream(candidate, sysPrompt, userPrompt, endpoint).collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        tokensReceived++
                        fullTextBuilder.append(event.text)
                        onToken?.invoke(event.text)
                    }
                    is StreamEvent.Completed -> {
                        // Successfully finished streaming
                    }
                    is StreamEvent.Error -> {
                        failureEncountered = event
                    }
                }
            }

            if (failureEncountered != null) {
                val err = failureEncountered!!
                Log.e("AINovel_Engine", "Chapter stream failed with code ${err.code}: ${err.message}")
                lastError = Exception("HTTP ${err.code}: ${err.message}")

                if (err.code == 429) {
                    rotationManager.reportRateLimit(candidate.modelId, candidate.keyHash, err.message)
                } else if (err.code == 503) {
                    rotationManager.reportServerOverloadOrTimeout(candidate.modelId, candidate.keyHash)
                }
                continue // Try next candidate
            }

            if (tokensReceived == 0) {
                Log.w("AINovel_Engine", "0 tokens received from candidate ${candidate.modelId}, failing over")
                rotationManager.reportEmptyStream(candidate.modelId, candidate.keyHash)
                lastError = Exception("Empty stream received (0 tokens)")
                continue
            }

            val finalContent = fullTextBuilder.toString().trim()
            val wordCount = finalContent.split(Regex("""\s+""")).filter { it.isNotBlank() }.size

            // Update chapter in repository
            novelRepository.updateChapterContent(
                chapterId = targetChapter.id,
                content = finalContent,
                wordCount = wordCount,
                status = "completed"
            )

            // Update novel overall progress
            val updatedAll = novelRepository.getChaptersList(novelId)
            val completedCount = updatedAll.count { it.status == "completed" }
            novelRepository.updateNovel(novel.copy(completedChapters = completedCount))

            val updatedChapter = novelRepository.getChapter(targetChapter.id) ?: targetChapter
            return@withContext Result.success(updatedChapter)
        }

        return@withContext Result.failure(lastError ?: Exception("Exceeded max rotation attempts"))
    }
}
