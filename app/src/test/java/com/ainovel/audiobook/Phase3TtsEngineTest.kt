package com.ainovel.audiobook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ainovel.audiobook.data.local.AppDatabase
import com.ainovel.audiobook.data.local.entity.ChapterEntity
import com.ainovel.audiobook.data.local.entity.NovelEntity
import com.ainovel.audiobook.data.repository.NovelRepositoryImpl
import com.ainovel.audiobook.domain.usecase.GenerateVoiceSamplePreviewUseCase
import com.ainovel.audiobook.domain.usecase.SynthesizeChapterToSpeechUseCase
import com.ainovel.audiobook.tts.engine.PcmUtils
import com.ainovel.audiobook.tts.engine.SeaG2P
import com.ainovel.audiobook.tts.engine.SmartTextSegmenter
import com.ainovel.audiobook.tts.engine.VieNeuConfig
import com.ainovel.audiobook.tts.engine.VoicePresets
import com.ainovel.audiobook.tts.engine.VoiceSamplePreviewManager
import com.ainovel.audiobook.tts.remote.RemoteVieNeuApiClient
import com.ainovel.audiobook.tts.storage.AudioStorageManager
import com.ainovel.audiobook.tts.storage.WavWriter
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Phase 03 Comprehensive Single Verification Test:
 * VieNeu-TTS Engine & Instant Voice Sample Studio.
 *
 * Verifies:
 * 1. VoiceSamplePreviewManager: Pre-bundled samples, on-demand quick synthesis, and multi-tier caching.
 * 2. SmartTextSegmenter: Punctuation rules, <en>...</en> preservation, and emotion tags across sentence bounds.
 * 3. WavWriter: Exact 44-byte RIFF header (48kHz, 16-bit Mono PCM), streaming append/finalize, and parse verification.
 * 4. SeaG2P: Vietnamese phonetic transformation, English loanword tag preservation, and inline emotion conversion.
 * 5. RemoteVieNeuApiClient: MockWebServer OpenAI-compatible `/v1/audio/speech` chunked stream decoding.
 * 6. AudioStorageManager & UseCases: Batch ZIP packaging and end-to-end chapter synthesis with RTF tracking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase3TtsEngineTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var novelRepo: NovelRepositoryImpl
    private lateinit var mockServer: MockWebServer
    private lateinit var previewManager: VoiceSamplePreviewManager
    private lateinit var remoteClient: RemoteVieNeuApiClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.createInMemoryDatabase(context)
        novelRepo = NovelRepositoryImpl(db.novelDao(), db.chapterDao(), db.outlineDao())

        mockServer = MockWebServer()
        mockServer.start()

        remoteClient = RemoteVieNeuApiClient(baseUrl = mockServer.url("").toString().removeSuffix("/"))
        previewManager = VoiceSamplePreviewManager(context, remoteApiClient = remoteClient)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
        db.close()
        previewManager.clearCache()
    }

    @Test
    fun testPhase3CoreFunctionality() = runBlocking {
        // =========================================================================
        // STEP 1: Voice Presets & Catalog Upgrade
        // =========================================================================
        val presets = VoicePresets.getVoicePresets()
        assertTrue("Should have 8+ curated voice presets", presets.size >= 8)

        val ngocHuyen = VoicePresets.getVoice("Ngọc Huyền")
        assertNotNull("Default voice Ngọc Huyền must exist", ngocHuyen)
        assertEquals("Nữ", ngocHuyen?.genderDisplay)
        assertEquals("Giọng Miền Bắc", ngocHuyen?.regionDisplay)
        assertTrue("Style should be storytelling or natural", ngocHuyen?.styleDisplay?.isNotEmpty() == true)

        val binh = VoicePresets.getVoice("Bình")
        assertNotNull("Voice Bình must exist", binh)
        assertEquals("Nam", binh?.genderDisplay)
        assertEquals("Giọng Miền Bắc", binh?.regionDisplay)

        val featured = VoicePresets.getFeaturedVoices()
        assertTrue("Featured voices should be registered", featured.isNotEmpty())

        // =========================================================================
        // STEP 2: SmartTextSegmenter & Special Tags (<en>, Emotions, Punctuation)
        // =========================================================================
        val complexText = """
            Giáo sư <en>John Doe</en> mỉm cười [cười] khi nhìn vào màn hình <en>AI Supercomputer</en>.
            - Chúng ta đã thành công rồi! – Ông thở dài [thở dài].
            Liệu tương lai có an toàn? Hay đây là khởi đầu của thảm họa?
        """.trimIndent()

        val chunks = SmartTextSegmenter.segment(complexText, maxCharsPerChunk = 150)
        assertTrue("Should segment text into multiple chunks", chunks.size >= 2)

        // Verify <en> and emotion tags are preserved in segmented chunks
        val firstChunk = chunks[0]
        assertTrue("Chunk 1 must retain <en>John Doe</en>", firstChunk.text.contains("<en>John Doe</en>"))
        assertTrue("Chunk 1 must retain [cười]", firstChunk.text.contains("[cười]"))

        // Verify punctuation normalization rules
        assertEquals("Thành phố.", SmartTextSegmenter.puncNorm("Thành phố"))
        assertEquals("Thế à.", SmartTextSegmenter.puncNorm("Thế à?")) // Short chunk (< 5 words) forced to '.'
        assertEquals("Đây là một câu rất dài có trên năm từ trong tiếng Việt?",
            SmartTextSegmenter.puncNorm("Đây là một câu rất dài có trên năm từ trong tiếng Việt?"))

        // Verify HTML stripping while preserving <en> tags
        val htmlText = "<p>Chào bạn <b>đồng nghiệp</b>, hãy đọc <en>Deep Learning Book</en> nhé!<br/>Hẹn gặp lại.</p>"
        val plainText = SmartTextSegmenter.htmlToStructuredText(htmlText)
        assertTrue("HTML tags stripped", !plainText.contains("<p>") && !plainText.contains("<b>"))
        assertTrue("English tag preserved", plainText.contains("<en>Deep Learning Book</en>"))

        // =========================================================================
        // STEP 3: Phonemizer (SeaG2P) with Bilingual <en> and Emotion Preservation
        // =========================================================================
        val sentenceWithEmotion = "Chào bạn [cười], tôi thấy rất mệt [thở dài]."
        val phonemizedEmotion = SeaG2P.phonemizeWithEmotions(sentenceWithEmotion)
        assertTrue("Should convert [cười] to <|emotion_1|>", phonemizedEmotion.contains("<|emotion_1|>"))
        assertTrue("Should convert [thở dài] to <|emotion_2|>", phonemizedEmotion.contains("<|emotion_2|>"))

        val sentenceWithEnglish = "Tôi thích đọc <en>Novel Studio</en> rất nhiều."
        val phonemizedEnglish = SeaG2P.phonemize(sentenceWithEnglish)
        assertTrue("Must keep <en>Novel Studio</en> intact", phonemizedEnglish.contains("<en>Novel Studio</en>"))

        // =========================================================================
        // STEP 4: WavWriter Lossless 48kHz 16-bit Mono RIFF & Streaming Append
        // =========================================================================
        val sampleRate = 48000
        val testSamples = ShortArray(48000) { i ->
            (Math.sin(2.0 * Math.PI * 440.0 * (i.toDouble() / sampleRate)) * 10000.0).toInt().toShort()
        } // 1 second of 440Hz tone

        val wavBytes = WavWriter.pcm16ToWav(testSamples, sampleRate = sampleRate, numChannels = 1)
        val expectedDataSize = testSamples.size * 2 // 96000 bytes
        val expectedTotalSize = WavWriter.HEADER_SIZE + expectedDataSize // 96044 bytes
        assertEquals("Total WAV bytes must equal header (44) + data (96000)", expectedTotalSize, wavBytes.size)

        // Validate RIFF header binary structures
        val buffer = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4)
        buffer.get(riff)
        assertEquals("RIFF", String(riff, Charsets.US_ASCII))

        val chunkSize = buffer.int
        assertEquals(36 + expectedDataSize, chunkSize)

        val wave = ByteArray(4)
        buffer.get(wave)
        assertEquals("WAVE", String(wave, Charsets.US_ASCII))

        val fmt = ByteArray(4)
        buffer.get(fmt)
        assertEquals("fmt ", String(fmt, Charsets.US_ASCII))

        val fmtSize = buffer.int
        assertEquals(16, fmtSize)

        val audioFormat = buffer.short
        assertEquals(1.toShort(), audioFormat) // PCM

        val numChannels = buffer.short
        assertEquals(1.toShort(), numChannels) // Mono

        val readSampleRate = buffer.int
        assertEquals(48000, readSampleRate)

        val byteRate = buffer.int
        assertEquals(96000, byteRate) // 48000 * 1 * 2

        val blockAlign = buffer.short
        assertEquals(2.toShort(), blockAlign)

        val bitsPerSample = buffer.short
        assertEquals(16.toShort(), bitsPerSample)

        // Read and parse WAV
        val parsedWav = WavWriter.readWav(wavBytes)
        assertEquals(48000, parsedWav.sampleRate)
        assertEquals(1, parsedWav.numChannels)
        assertEquals(16, parsedWav.bitsPerSample)
        assertEquals(expectedDataSize, parsedWav.dataSize)
        assertEquals(testSamples.size, parsedWav.pcm16.size)
        assertEquals(1.0f, parsedWav.durationSeconds, 0.01f)

        // Test Streaming Append & Finalize
        val tempStreamFile = File(context.cacheDir, "stream_test_${System.currentTimeMillis()}.wav")
        WavWriter.initStreamingWav(tempStreamFile, sampleRate = 48000, numChannels = 1)
        val chunk1 = ShortArray(24000) { 1000 }
        val chunk2 = ShortArray(24000) { 2000 }
        WavWriter.appendPcmChunk(tempStreamFile, chunk1)
        WavWriter.appendPcmChunk(tempStreamFile, chunk2)
        WavWriter.finalizeStreamingWav(tempStreamFile)

        val parsedStreamWav = WavWriter.readWav(tempStreamFile)
        assertEquals(48000, parsedStreamWav.sampleRate)
        assertEquals(48000, parsedStreamWav.pcm16.size)
        assertEquals(1.0f, parsedStreamWav.durationSeconds, 0.01f)
        tempStreamFile.delete()

        // =========================================================================
        // STEP 5: VoiceSamplePreviewManager (Bundled Samples & Quick Synthesizer)
        // =========================================================================
        // Check bundled samples resolution for default voices
        val bundledNgocHuyen = previewManager.getBundledSample("Ngọc Huyền")
        assertNotNull("Bundled sample for Ngọc Huyền must be resolved from assets", bundledNgocHuyen)
        assertTrue("Sample bytes must be non-empty", bundledNgocHuyen!!.isNotEmpty())

        val parsedBundled = WavWriter.readWav(bundledNgocHuyen)
        assertTrue("Bundled sample should have valid audio rate (24000 or 48000)", parsedBundled.sampleRate == 24000 || parsedBundled.sampleRate == 48000)

        // Check on-demand quick synthesis for unbundled/custom voice
        val customVoiceSample = previewManager.getOrGenerateSample("CustomVoice99", useRemoteFallback = false)
        assertNotNull("Should generate on-demand preview sample", customVoiceSample)
        assertTrue("Sample bytes must be non-empty", customVoiceSample.isNotEmpty())

        // Verify disk cache persistence
        val cachedFile = previewManager.getDiskCachedFile("CustomVoice99")
        assertTrue("Disk cache file must exist", cachedFile.exists() && cachedFile.length() > 0)

        // Verify UseCase wrapping preview manager
        val previewUseCase = GenerateVoiceSamplePreviewUseCase(previewManager)
        val previewResult = previewUseCase("Ngọc Huyền")
        assertTrue("GenerateVoiceSamplePreviewUseCase should succeed", previewResult.isSuccess)
        assertEquals(bundledNgocHuyen.size, previewResult.getOrThrow().size)

        // =========================================================================
        // STEP 6: Remote API Fallback with Chunked Stream Decoding
        // =========================================================================
        val mockRemotePcm = ShortArray(48000) { 500 }
        val mockRemoteWav = WavWriter.pcm16ToWav(mockRemotePcm)

        // Simulate chunked streaming HTTP response from MockWebServer
        val chunk1Bytes = mockRemoteWav.copyOfRange(0, 10000)
        val chunk2Bytes = mockRemoteWav.copyOfRange(10000, 50000)
        val chunk3Bytes = mockRemoteWav.copyOfRange(50000, mockRemoteWav.size)

        val responseBuffer = Buffer()
        responseBuffer.write(chunk1Bytes)
        responseBuffer.write(chunk2Bytes)
        responseBuffer.write(chunk3Bytes)

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setChunkedBody(responseBuffer, 10000)
        )

        val streamFlow = remoteClient.synthesizeSpeechStream("Xin chào thế giới", voice = "Ngọc Huyền")
        val receivedChunks = streamFlow.toList()
        assertTrue("Should receive multiple incoming chunks", receivedChunks.size >= 2)

        val totalDecodedBytes = receivedChunks.sumOf { it.size }
        assertEquals("Total decoded streaming bytes must equal original WAV size", mockRemoteWav.size, totalDecodedBytes)

        val collectedWav = remoteClient.collectStreamToWav(receivedChunks.asFlow())
        val parsedCollected = WavWriter.readWav(collectedWav)
        assertEquals(48000, parsedCollected.sampleRate)
        assertEquals(mockRemotePcm.size, parsedCollected.pcm16.size)

        // =========================================================================
        // STEP 7: AudioStorageManager Batch ZIP Packaging
        // =========================================================================
        val file1 = File(context.cacheDir, "chap1.wav").apply { WavWriter.writeWav(this, ShortArray(24000)) }
        val file2 = File(context.cacheDir, "chap2.wav").apply { WavWriter.writeWav(this, ShortArray(24000)) }
        val zipDest = File(context.cacheDir, "audiobook_chapters.zip")

        AudioStorageManager.createZipArchive(listOf(file1, file2), zipDest)
        assertTrue("ZIP archive must be generated", zipDest.exists() && zipDest.length() > 0)
        file1.delete()
        file2.delete()
        zipDest.delete()

        // =========================================================================
        // STEP 8: SynthesizeChapterToSpeechUseCase End-to-End Orchestration
        // =========================================================================
        val novel = novelRepo.createNovel("Thiên Thần Sa Ngã", "Truyện kỳ ảo", "fantasy", "vi")
        val chapter = ChapterEntity(
            id = UUID.randomUUID().toString(),
            novelId = novel.id,
            chapterIndex = 1,
            title = "Khởi Nguyên Của Bão Tố",
            content = "Cơn gió đêm rít qua những tán cây cổ thụ. Thành phố chìm trong tĩnh mịch."
        )
        novelRepo.saveChapter(chapter)

        // Mock remote speech synthesis response for the chapter
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(Buffer().write(mockRemoteWav))
        )

        var reportedProgress = false
        val chapterSynthUseCase = SynthesizeChapterToSpeechUseCase(context, novelRepo, remoteClient)
        val synthResult = chapterSynthUseCase(
            chapterId = chapter.id,
            voiceName = "Ngọc Huyền",
            onProgress = { cur, total, pct ->
                reportedProgress = true
            }
        )

        assertTrue("Chapter synthesis should succeed", synthResult.isSuccess)
        val audioData = synthResult.getOrThrow()
        assertEquals(chapter.id, audioData.chapterId)
        assertTrue("Audio duration should be positive", audioData.durationMs > 0)
        assertTrue("Real-Time Factor (RTF) should be tracked", audioData.realTimeFactor >= 0f)
        assertTrue("Audio file must exist on disk", File(audioData.audioPath).exists())
        assertTrue("Progress callback was invoked", reportedProgress)

        // Verify chapter audio metadata was persisted in DB
        val updatedChapter = novelRepo.getChapter(chapter.id)
        assertNotNull(updatedChapter)
        assertEquals(audioData.audioPath, updatedChapter?.audioFilePath)
        assertEquals(audioData.durationMs, updatedChapter?.audioDurationMs)

        println(">>> [Phase3TtsEngineTest] ALL VIENEU-TTS ENGINE & VOICE STUDIO TESTS PASSED! <<<")
    }
}
