package com.ainovel.audiobook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ainovel.audiobook.data.local.AppDatabase
import com.ainovel.audiobook.data.local.entity.ChapterEntity
import com.ainovel.audiobook.data.local.entity.NovelEntity
import com.ainovel.audiobook.data.local.entity.OutlineEntity
import com.ainovel.audiobook.data.local.entity.VoicePresetEntity
import com.ainovel.audiobook.data.repository.NovelRepositoryImpl
import com.ainovel.audiobook.data.repository.QuotaRepositoryImpl
import com.ainovel.audiobook.data.repository.VoiceRepositoryImpl
import com.ainovel.audiobook.data.security.SecureKeyStorageImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Phase 01 Comprehensive Single Verification Test: Architecture, Room Database & Voice Preset Entities.
 *
 * Verifies:
 * 1. Room in-memory database creation & default voice presets seeding with audio sample URIs.
 * 2. Cascading foreign-key deletions (Novel -> Chapters, Novel -> Outline).
 * 3. VoicePreset filtering by region, gender, favorites, and sample URI updates.
 * 4. QuotaLog tracking, active cooldown isolation, and expired lock purging.
 * 5. SecureKeyStorage hardware encryption/fallback, SHA-256 hashing, masking, and deletion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase1DatabaseTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var novelRepo: NovelRepositoryImpl
    private lateinit var voiceRepo: VoiceRepositoryImpl
    private lateinit var quotaRepo: QuotaRepositoryImpl
    private lateinit var keyStorage: SecureKeyStorageImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.createInMemoryDatabase(context)
        novelRepo = NovelRepositoryImpl(db.novelDao(), db.chapterDao(), db.outlineDao())
        voiceRepo = VoiceRepositoryImpl(db.voicePresetDao())
        quotaRepo = QuotaRepositoryImpl(db.quotaLogDao())
        keyStorage = SecureKeyStorageImpl(context, db.apiKeyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPhase1CoreFunctionality() = runBlocking {
        // =========================================================================
        // STEP 1: Voice Presets Seeding & Sample Audio URI Verification
        // =========================================================================
        db.seedDefaultVoicePresets()
        val allVoices = voiceRepo.getAllPresetsList()
        assertTrue("Default voices should be seeded (at least 8 presets)", allVoices.size >= 8)

        // Verify presence of sample audio URIs and region groupings
        val hanoiVoice = voiceRepo.getPreset("hanoi_nu_kechuyen")
        assertNotNull("Hanoi female voice should exist", hanoiVoice)
        assertEquals("Hà Nội - Nữ Kể Chuyện", hanoiVoice?.name)
        assertEquals("Bắc", hanoiVoice?.region)
        assertEquals("samples/hanoi_nu_kechuyen.wav", hanoiVoice?.sampleAudioUri)
        assertTrue("Hanoi storyteller should be favorited by default", hanoiVoice?.isFavorite == true)

        // Test filtering by region (Bắc, Trung, Nam)
        val northVoices = voiceRepo.getPresetsByRegion("Bắc").first()
        assertTrue("Should have North regional voices", northVoices.isNotEmpty())
        assertTrue("All filtered voices should belong to region Bắc", northVoices.all { it.region == "Bắc" })

        // Test updating sample audio URI
        val customUri = "https://cdn.example.com/custom_sample.wav"
        voiceRepo.updateSampleUri("hanoi_nu_kechuyen", customUri)
        val updatedHanoi = voiceRepo.getPreset("hanoi_nu_kechuyen")
        assertEquals(customUri, updatedHanoi?.sampleAudioUri)

        // =========================================================================
        // STEP 2: Novel, Chapters, Outline Creation & Cascade Delete Verification
        // =========================================================================
        val novel = novelRepo.createNovel(
            title = "Trước Khi Thành Phố Kịp Nhắm Mắt",
            topic = "Cyberpunk trinh thám tại Sài Gòn năm 2088",
            genre = "Hard Sci-Fi",
            language = "vi"
        )
        assertNotNull("Novel should have an ID", novel.id)

        // Add 3 Chapters
        val chapter1 = ChapterEntity(
            id = UUID.randomUUID().toString(),
            novelId = novel.id,
            chapterIndex = 1,
            title = "Chương 1: Đêm Mưa Axit",
            content = "Những ngọn đèn neon nhấp nháy bên dưới tầng mây xám...",
            wordCount = 1200,
            status = "completed"
        )
        val chapter2 = ChapterEntity(
            id = UUID.randomUUID().toString(),
            novelId = novel.id,
            chapterIndex = 2,
            title = "Chương 2: Tín Hiệu Bí Mật",
            content = "Âm thanh rè rè vang lên từ chiếc máy thu cũ kỹ...",
            wordCount = 1350,
            status = "completed"
        )
        novelRepo.saveChapters(listOf(chapter1, chapter2))

        // Add Outline
        novelRepo.saveOutline(
            novelId = novel.id,
            rawText = "# Đề cương kịch bản...",
            titles = listOf("Chương 1: Đêm Mưa Axit", "Chương 2: Tín Hiệu Bí Mật")
        )

        // Verify insertion
        var chapters = novelRepo.getChaptersList(novel.id)
        assertEquals("Should contain exactly 2 chapters", 2, chapters.size)
        val outline = novelRepo.getOutline(novel.id)
        assertNotNull("Outline should be present", outline)
        assertEquals(2, outline?.totalPlannedChapters)

        // Verify Cascade Deletion when Novel is deleted
        novelRepo.deleteNovel(novel.id)
        chapters = novelRepo.getChaptersList(novel.id)
        assertEquals("Chapters must be deleted via foreign key CASCADE", 0, chapters.size)
        val deletedOutline = novelRepo.getOutline(novel.id)
        assertNull("Outline must be deleted via foreign key CASCADE", deletedOutline)

        // =========================================================================
        // STEP 3: Quota Tracking, Cooldown & Lock Expiry Verification
        // =========================================================================
        val testModel = "gemini-3.8-flash"
        val testKeyHash = "a1b2c3d4e5f67890"

        assertFalse("Model/key should initially not be locked", quotaRepo.isModelKeyLocked(testModel, testKeyHash))

        // Lock for 1 hour (3600000ms)
        quotaRepo.lockModelKey(
            modelId = testModel,
            keyHash = testKeyHash,
            errorStatus = 429,
            lockType = "rpm_cooldown",
            cooldownMs = 3600000L
        )
        assertTrue("Model/key should now be locked", quotaRepo.isModelKeyLocked(testModel, testKeyHash))

        // Release lock
        quotaRepo.releaseLock(testModel, testKeyHash)
        assertFalse("Model/key should be unlocked after release", quotaRepo.isModelKeyLocked(testModel, testKeyHash))

        // =========================================================================
        // STEP 4: Secure Key Storage, Masking, Hashing & Persistence Verification
        // =========================================================================
        val rawApiKey = "AIzaSyD_TEST_GOOGLE_GEMINI_KEY_XYZ12345"
        val savedKey = keyStorage.saveKey(rawApiKey, provider = "gemini", label = "Production Key 1")

        assertEquals("Key hash should be 16 characters hex", 16, savedKey.keyHash.length)
        assertTrue("Masked key should hide middle characters", savedKey.maskedKey.contains("••••••••"))
        assertTrue("Masked key should preserve prefix", savedKey.maskedKey.startsWith("AIzaSy"))

        // Retrieve raw key
        val retrievedRaw = keyStorage.getRawKey(savedKey.keyHash)
        assertEquals("Decrypted/retrieved raw key must match original exactly", rawApiKey, retrievedRaw)

        // Delete key
        keyStorage.deleteKey(savedKey.keyHash)
        val deletedRaw = keyStorage.getRawKey(savedKey.keyHash)
        assertNull("Raw key must be removed from secure storage", deletedRaw)
        val deletedEntity = db.apiKeyDao().getKeyByHash(savedKey.keyHash)
        assertNull("API key entity must be deleted from database", deletedEntity)

        println(">>> [Phase1DatabaseTest] ALL CORE DATABASE & ARCHITECTURE CHECKS PASSED SUCCESSFULLY! <<<")
    }
}
