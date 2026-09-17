package com.ainovel.audiobook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ainovel.audiobook.data.local.AppDatabase
import com.ainovel.audiobook.data.local.entity.ChapterEntity
import com.ainovel.audiobook.data.remote.OkHttpLlmClient
import com.ainovel.audiobook.data.remote.PromptTemplateService
import com.ainovel.audiobook.data.repository.NovelRepositoryImpl
import com.ainovel.audiobook.data.repository.QuotaRepositoryImpl
import com.ainovel.audiobook.data.security.SecureKeyStorageImpl
import com.ainovel.audiobook.domain.engine.NovelGenerationEngine
import com.ainovel.audiobook.domain.engine.RotationManager
import com.ainovel.audiobook.domain.engine.SlidingWindowContextManager
import com.ainovel.audiobook.domain.model.ModelConfig
import com.ainovel.audiobook.domain.model.StreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Phase 02 Comprehensive Single Verification Test: AI Novel Writing Engine & Rotation Subsystem.
 *
 * Verifies:
 * 1. Title sanitization removing brackets `[...]`, `《...》`, `**...**`.
 * 2. Sliding-Window Context Manager assembly preserving 3 recent chapters and distant summaries.
 * 3. Model-First Key-Second rotation algorithm:
 *    - Prefers higher-priority models across all active keys first.
 *    - Differentiates 429 RPM (short cooldown) vs 429 RPD (30-hour lockout).
 *    - Detects 0-token empty stream and initiates instant candidate failover.
 * 4. OkHttp SSE token streaming with MockWebServer.
 * 5. Full end-to-end novel outlining and chapter generation cycle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase2AiRotationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var mockServer: MockWebServer
    private lateinit var keyStorage: SecureKeyStorageImpl
    private lateinit var quotaRepo: QuotaRepositoryImpl
    private lateinit var novelRepo: NovelRepositoryImpl
    private lateinit var rotationManager: RotationManager
    private lateinit var promptService: PromptTemplateService
    private lateinit var contextManager: SlidingWindowContextManager
    private lateinit var llmClient: OkHttpLlmClient
    private lateinit var generationEngine: NovelGenerationEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.createInMemoryDatabase(context)
        mockServer = MockWebServer()
        mockServer.start()

        keyStorage = SecureKeyStorageImpl(context, db.apiKeyDao())
        quotaRepo = QuotaRepositoryImpl(db.quotaLogDao())
        novelRepo = NovelRepositoryImpl(db.novelDao(), db.chapterDao(), db.outlineDao())

        val models = listOf(
            ModelConfig(id = "gemini-3.7-flash", priority = 1, rpmCooldownMs = 1000L, rpdExhaustedMs = 108_000_000L),
            ModelConfig(id = "gemini-3.5-flash-lite", priority = 2, rpmCooldownMs = 1000L, rpdExhaustedMs = 108_000_000L)
        )

        rotationManager = RotationManager(db.apiKeyDao(), keyStorage, quotaRepo, models)
        promptService = PromptTemplateService()
        contextManager = SlidingWindowContextManager(windowSize = 3)
        llmClient = OkHttpLlmClient(defaultBaseUrl = mockServer.url("/chat/completions").toString())

        generationEngine = NovelGenerationEngine(
            rotationManager = rotationManager,
            llmClient = llmClient,
            promptService = promptService,
            contextManager = contextManager,
            novelRepository = novelRepo,
            customEndpointUrl = mockServer.url("/chat/completions").toString()
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
        db.close()
    }

    @Test
    fun testPhase2CoreFunctionality() = runBlocking {
        // =========================================================================
        // STEP 1: Title Sanitization & Outline Title Extraction
        // =========================================================================
        assertEquals("Đêm Mưa Axit", promptService.sanitizeChapterTitle("[Đêm Mưa Axit]"))
        assertEquals("Bí Mật Thành Phố", promptService.sanitizeChapterTitle("《Bí Mật Thành Phố》"))
        assertEquals("Khai Màn", promptService.sanitizeChapterTitle("**[Khai Màn]**"))

        val mockOutlineText = """
            # ĐỀ CƯƠNG CHI TIẾT
            Chương 1: [Khởi Đầu Bí Ẩn]
            Nhân vật chính phát hiện tín hiệu lạ.
            Chương 2: 《Đột Nhập Trạm Phát Sóng》
            Nhóm thám hiểm tiến vào khu vực cấm.
            Chương 3: **Trận Chiến Dưới Mưa**
            Giao tranh nổ ra.
        """.trimIndent()

        val extracted = promptService.extractChapterTitles(mockOutlineText)
        assertEquals(3, extracted.size)
        assertEquals("Chương 1: Khởi Đầu Bí Ẩn", extracted[0])
        assertEquals("Chương 2: Đột Nhập Trạm Phát Sóng", extracted[1])
        assertEquals("Chương 3: Trận Chiến Dưới Mưa", extracted[2])

        // =========================================================================
        // STEP 2: Sliding-Window Context Assembly (4 Chapters Sequence)
        // =========================================================================
        val chaptersList = listOf(
            ChapterEntity(UUID.randomUUID().toString(), "n1", 1, "Chương 1", "Nội dung chương 1 dài...", "Tóm tắt chương 1"),
            ChapterEntity(UUID.randomUUID().toString(), "n1", 2, "Chương 2", "Nội dung chương 2 dài...", "Tóm tắt chương 2"),
            ChapterEntity(UUID.randomUUID().toString(), "n1", 3, "Chương 3", "Nội dung chương 3 dài...", "Tóm tắt chương 3"),
            ChapterEntity(UUID.randomUUID().toString(), "n1", 4, "Chương 4", "Nội dung chương 4 dài...", "Tóm tắt chương 4")
        )

        val (recentText, grandSummary) = contextManager.assembleContext(chaptersList)
        // Window size is 3 -> recent should contain chapters 2, 3, 4
        assertTrue("Recent context should contain Chapter 2", recentText.contains("CHƯƠNG 2: Chương 2"))
        assertTrue("Recent context should contain Chapter 3", recentText.contains("CHƯƠNG 3: Chương 3"))
        assertTrue("Recent context should contain Chapter 4", recentText.contains("CHƯƠNG 4: Chương 4"))
        assertFalse("Recent context should not contain Chapter 1 (dropped into distant summary)", recentText.contains("CHƯƠNG 1: Chương 1"))

        // Distant grand summary should contain Chapter 1 summary
        assertTrue("Grand summary should retain Chapter 1 summary", grandSummary.contains("Tóm tắt chương 1"))

        // =========================================================================
        // STEP 3: Model-First Key-Second Rotation & 429 Handling
        // =========================================================================
        val key1 = keyStorage.saveKey("AIzaSyKeyOne111111111111111111111111", provider = "gemini", label = "Key 1")
        val key2 = keyStorage.saveKey("AIzaSyKeyTwo222222222222222222222222", provider = "gemini", label = "Key 2")

        // 1st election: Should choose Priority 1 model (gemini-3.7-flash) with Key 1
        var candidate = rotationManager.electCandidate()
        assertNotNull(candidate)
        assertEquals("gemini-3.7-flash", candidate?.modelId)
        assertEquals(key1.keyHash, candidate?.keyHash)

        // Simulate 429 RPM spike on (gemini-3.7-flash, Key 1)
        val rpmErrorBody = """
            {
              "error": {
                "code": 429,
                "message": "ResourceExhausted: Per-minute rate limit exceeded",
                "status": "RESOURCE_EXHAUSTED",
                "details": [{"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "30s"}]
              }
            }
        """.trimIndent()
        rotationManager.reportRateLimit("gemini-3.7-flash", key1.keyHash, responseBody = rpmErrorBody)

        // 2nd election: Under Model-First Key-Second, should choose (gemini-3.7-flash, Key 2)
        candidate = rotationManager.electCandidate()
        assertNotNull(candidate)
        assertEquals("gemini-3.7-flash", candidate?.modelId)
        assertEquals(key2.keyHash, candidate?.keyHash)

        // Simulate 429 RPD daily exhaustion on (gemini-3.7-flash, Key 2)
        val rpdErrorBody = """
            {
              "error": {
                "code": 429,
                "message": "ResourceExhausted: Daily request quota exceeded",
                "status": "RESOURCE_EXHAUSTED"
              }
            }
        """.trimIndent()
        rotationManager.reportRateLimit("gemini-3.7-flash", key2.keyHash, responseBody = rpdErrorBody)

        // 3rd election: All keys exhausted on gemini-3.7-flash -> Falls back to Model 2 (gemini-3.5-flash-lite, Key 1)
        candidate = rotationManager.electCandidate()
        assertNotNull(candidate)
        assertEquals("gemini-3.5-flash-lite", candidate?.modelId)
        assertEquals(key1.keyHash, candidate?.keyHash)

        // =========================================================================
        // STEP 4: OkHttp SSE Streaming Flow Verification
        // =========================================================================
        val sseBody = """
            data: {"choices":[{"delta":{"content":"Đêm nay, "}}]}

            data: {"choices":[{"delta":{"content":"thành phố "}}]}

            data: {"choices":[{"delta":{"content":"vẫn sáng đèn."}}]}

            data: [DONE]
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody)
        )

        val streamEvents = llmClient.chatStream(candidate!!, "System prompt", "User prompt").toList()
        val tokens = streamEvents.filterIsInstance<StreamEvent.Token>()
        assertEquals(3, tokens.size)
        assertEquals("Đêm nay, ", tokens[0].text)
        assertEquals("thành phố ", tokens[1].text)
        assertEquals("vẫn sáng đèn.", tokens[2].text)

        val completed = streamEvents.filterIsInstance<StreamEvent.Completed>().firstOrNull()
        assertNotNull(completed)
        assertEquals("Đêm nay, thành phố vẫn sáng đèn.", completed?.fullText)
        assertEquals(3, completed?.totalTokens)

        // =========================================================================
        // STEP 5: Full Engine Outlining & Chapter Generation with Rotation Fallback
        // =========================================================================
        val novel = novelRepo.createNovel(
            title = "Thành Phố Hoàng Hôn",
            topic = "Thám tử tư điều tra vụ mất tích kỳ bí tại Sài Gòn",
            genre = "mystery",
            language = "vi"
        )

        // Mock response for Outline Generation
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {
                      "choices": [{
                        "message": {
                          "content": "Chương 1: [Vụ Án Mở Đầu]\nThám tử nhận nhiệm vụ.\nChương 2: [Manh Mối Trong Đêm]\nTruy tìm dấu vết."
                        }
                      }]
                    }
                """.trimIndent())
        )

        val outlineResult = generationEngine.generateOutline(
            novelId = novel.id,
            topic = novel.topic,
            genre = novel.genre,
            totalChapters = 2,
            language = "vi"
        )
        assertTrue("Outline generation should succeed", outlineResult.isSuccess)
        val chapters = novelRepo.getChaptersList(novel.id)
        assertEquals(2, chapters.size)
        assertEquals("Chương 1: Vụ Án Mở Đầu", chapters[0].title)

        // Mock 1st attempt: 0-token empty stream -> Triggers rotation fallback
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: [DONE]\n\n")
        )

        // Mock 2nd attempt: Success stream on failover candidate
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("""
                    data: {"choices":[{"delta":{"content":"Gió rít qua khe cửa sổ. "}}]}

                    data: {"choices":[{"delta":{"content":"Thám tử chậm rãi châm điếu thuốc."}}]}

                    data: [DONE]
                """.trimIndent())
        )

        val streamedTokens = mutableListOf<String>()
        val chapterGenResult = generationEngine.generateChapter(novel.id, 1) { token ->
            streamedTokens.add(token)
        }

        assertTrue("Chapter generation should succeed after automatic 0-token failover", chapterGenResult.isSuccess)
        val generatedChapter = chapterGenResult.getOrNull()
        assertNotNull(generatedChapter)
        assertEquals("completed", generatedChapter?.status)
        assertTrue("Content should be populated", generatedChapter?.content?.contains("Thám tử chậm rãi") == true)
        assertTrue("Streamed tokens should be received", streamedTokens.isNotEmpty())

        val updatedNovel = novelRepo.getNovel(novel.id)
        assertEquals("Progress should update to 1 completed chapter", 1, updatedNovel?.completedChapters)

        println(">>> [Phase2AiRotationTest] ALL AI NOVEL ROTATION & GENERATION ENGINE TESTS PASSED! <<<")
    }
}
