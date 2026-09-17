package com.ainovel.audiobook

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.ainovel.audiobook.player.AudioPlayerManager
import com.ainovel.audiobook.player.MediaPlayerAdapter
import com.ainovel.audiobook.player.PlaybackProgressTracker
import com.ainovel.audiobook.player.PlaybackStatus
import com.ainovel.audiobook.player.PlayerState
import com.ainovel.audiobook.player.VoiceSamplePlayer
import com.ainovel.audiobook.player.WaveformSampler
import com.ainovel.audiobook.service.NotificationHelper
import com.ainovel.audiobook.service.NovelForegroundService
import com.ainovel.audiobook.service.ServiceMode
import com.ainovel.audiobook.tts.storage.WavWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Phase 04 Comprehensive Single Verification Test:
 * Background Foreground Service & Dual Audio Player Subsystem.
 *
 * Verifies:
 * 1. NotificationHelper: Notification channel initialization, custom action pending intents,
 *    Text Generation progress strings ("Writing Chapter 3/15 (45%) • 48 tokens/s [Pause] [Cancel]"),
 *    Audio Synthesis progress strings ("Synthesizing Audio: Chapter 3 (Seg 12/28) • RTF 0.85x [Pause] [Cancel]"),
 *    and Media Playback lockscreen notifications.
 * 2. NovelForegroundService: Partial WakeLock acquisition, safe idempotent release lifecycles,
 *    and Intent action command routing (START_TEXT, START_AUDIO, PAUSE, RESUME, CANCEL).
 * 3. Primary AudioPlayerManager: 48kHz WAV audio lifecycle, MediaSession metadata (Title, Chapter, Duration),
 *    smooth 50ms polling loop (~20fps), precision scrubber seeking (`seekTo(ms)` & `seekToFraction(ratio)`),
 *    and deterministic state machine transitions (Idle -> Prepared -> Playing -> Paused -> Completed -> Error).
 * 4. Dual Audio Player Subsystem: VoiceSamplePlayer plays preview clips independently inside Voice Picker Studio
 *    and automatically stops previous preview upon selecting a new voice without disrupting Primary Audiobook Player.
 * 5. WaveformSampler: Extracts 40-bar dynamic amplitude normalized vectors ([0.0f, 1.0f]) across empty,
 *    small, modulated sinusoidal RMS & Peak waveforms, float PCM, WAV bytes, and disk WAV file caching.
 * 6. PlaybackProgressTracker: Accurate bar indexing, normalized progress calculations, and reactive StateFlow tracking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase4ServicePlayerTest {

    private lateinit var context: Context

    /**
     * Controllable MediaPlayerAdapter for deterministic testing.
     */
    private class FakeMediaPlayerAdapter : MediaPlayerAdapter {
        var isPlayingInternal = false
        var currentPositionInternal = 0
        var durationInternal = 60000 // 60 seconds default
        var isPrepared = false
        var isReleased = false
        var storedDataSource: String? = null

        var completionCallback: (() -> Unit)? = null
        var errorCallback: ((Int, Int) -> Boolean)? = null

        override fun setDataSource(path: String) {
            if (path.contains("invalid_path")) {
                throw IllegalArgumentException("Invalid data source path: $path")
            }
            storedDataSource = path
        }

        override fun prepare() {
            if (storedDataSource == null) throw IllegalStateException("No data source set")
            isPrepared = true
        }

        override fun start() {
            if (!isPrepared) throw IllegalStateException("Player not prepared")
            isPlayingInternal = true
        }

        override fun pause() {
            isPlayingInternal = false
        }

        override fun stop() {
            isPlayingInternal = false
        }

        override fun seekTo(msec: Int) {
            currentPositionInternal = msec.coerceIn(0, durationInternal)
        }

        override fun isPlaying(): Boolean = isPlayingInternal
        override fun getCurrentPosition(): Int = currentPositionInternal
        override fun getDuration(): Int = durationInternal

        override fun reset() {
            isPlayingInternal = false
            currentPositionInternal = 0
            isPrepared = false
            storedDataSource = null
        }

        override fun release() {
            reset()
            isReleased = true
        }

        override fun setOnCompletionListener(listener: (() -> Unit)?) {
            completionCallback = listener
        }

        override fun setOnErrorListener(listener: ((what: Int, extra: Int) -> Boolean)?) {
            errorCallback = listener
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NovelForegroundService.resetStateForTesting()
        WaveformSampler.clearCache()
    }

    @After
    fun tearDown() {
        NovelForegroundService.resetStateForTesting()
        WaveformSampler.clearCache()
    }

    @Test
    fun verifyPhase4ForegroundServiceAndDualPlayerSubsystem() = runTest {
        // =========================================================================
        // SECTION 1: NotificationHelper & Intent Action Construction
        // =========================================================================
        NotificationHelper.createNotificationChannels(context)

        // 1.1 Text Generation Notification
        val textNotif = NotificationHelper.buildTextGenerationNotification(
            context = context,
            currentChapter = 3,
            totalChapters = 15,
            percent = 45,
            tokensPerSec = 48f,
            isPaused = false
        )
        val textExtras = NotificationCompat.getExtras(textNotif)
        val textContent = textExtras?.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
        assertNotNull(textContent)
        assertTrue("Text must contain chapter and percent", textContent!!.contains("Chapter 3/15 (45%)"))
        assertTrue("Text must contain speed tokens/s", textContent.contains("48 tokens/s"))
        assertEquals(2, textNotif.actions.size) // [Pause] and [Cancel]
        assertEquals("Pause", textNotif.actions[0].title.toString())
        assertEquals("Cancel", textNotif.actions[1].title.toString())

        // 1.2 Paused Text Generation Notification
        val textNotifPaused = NotificationHelper.buildTextGenerationNotification(
            context = context,
            currentChapter = 3,
            totalChapters = 15,
            percent = 45,
            tokensPerSec = 48f,
            isPaused = true
        )
        val pausedExtras = NotificationCompat.getExtras(textNotifPaused)
        val pausedText = pausedExtras?.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
        assertNotNull(pausedText)
        assertTrue("Must indicate paused", pausedText!!.contains("Paused"))
        assertEquals("Resume", textNotifPaused.actions[0].title.toString())

        // 1.3 Audio Synthesis Notification
        val audioNotif = NotificationHelper.buildAudioSynthesisNotification(
            context = context,
            chapterIndex = 3,
            currentSeg = 12,
            totalSegs = 28,
            rtf = 0.85f,
            isPaused = false
        )
        val audioExtras = NotificationCompat.getExtras(audioNotif)
        val audioContent = audioExtras?.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
        assertNotNull(audioContent)
        assertTrue("Audio text must contain segment progress", audioContent!!.contains("Chapter 3 (Seg 12/28)"))
        assertTrue("Audio text must contain RTF", audioContent.contains("RTF 0.85x"))
        assertEquals(2, audioNotif.actions.size) // [Pause] and [Cancel]

        // 1.4 Media Playback Notification
        val mediaNotif = NotificationHelper.buildMediaPlaybackNotification(
            context = context,
            title = "Dấu Chân Người Lính",
            chapterTitle = "Chương 3: Khúc Quân Hành",
            isPlaying = true
        )
        val mediaExtras = NotificationCompat.getExtras(mediaNotif)
        assertEquals("Dấu Chân Người Lính", mediaExtras?.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString())
        assertEquals("Chương 3: Khúc Quân Hành", mediaExtras?.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString())
        assertEquals(2, mediaNotif.actions.size) // [Pause] and [Stop]

        // =========================================================================
        // SECTION 2: NovelForegroundService & WakeLock Lifecycle
        // =========================================================================
        val serviceController = Robolectric.buildService(NovelForegroundService::class.java).create()
        val service = serviceController.get()

        // 2.1 WakeLock Acquisition and Safe Idempotent Release
        assertFalse("WakeLock should be unheld initially", service.isWakeLockHeld())
        service.acquireWakeLock()
        assertTrue("WakeLock must be held after acquire", service.isWakeLockHeld())
        // Re-acquiring while already held should not crash
        service.acquireWakeLock()
        assertTrue(service.isWakeLockHeld())

        service.releaseWakeLock()
        assertFalse("WakeLock must be released", service.isWakeLockHeld())
        // Idempotent release should not crash
        service.releaseWakeLock()
        assertFalse(service.isWakeLockHeld())

        // 2.2 Intent Action Routing: Start Text Generation
        val textIntent = Intent(context, NovelForegroundService::class.java).apply {
            action = NotificationHelper.ACTION_START_TEXT
            putExtra(NotificationHelper.EXTRA_CURRENT_CHAPTER, 3)
            putExtra(NotificationHelper.EXTRA_TOTAL_CHAPTERS, 15)
            putExtra(NotificationHelper.EXTRA_PROGRESS_PERCENT, 45)
            putExtra(NotificationHelper.EXTRA_TOKENS_PER_SEC, 48f)
        }
        service.onStartCommand(textIntent, 0, 1)
        var state = NovelForegroundService.serviceState.value
        assertTrue("Service must be running", state.isRunning)
        assertFalse("Service must not be paused", state.isPaused)
        assertEquals(ServiceMode.TEXT_GENERATION, state.mode)
        assertEquals(3, state.currentChapter)
        assertEquals(15, state.totalChapters)
        assertEquals(45, state.progressPercent)
        assertEquals(48f, state.tokensPerSec, 0.001f)
        assertTrue("WakeLock must be held during text generation", service.isWakeLockHeld())

        // 2.3 Real-time Text Progress Update
        service.updateTextProgress(3, 15, 60, 52f)
        state = NovelForegroundService.serviceState.value
        assertEquals(60, state.progressPercent)
        assertEquals(52f, state.tokensPerSec, 0.001f)

        // 2.4 Pause Action
        val pauseIntent = Intent(context, NovelForegroundService::class.java).apply {
            action = NotificationHelper.ACTION_PAUSE
        }
        service.onStartCommand(pauseIntent, 0, 2)
        state = NovelForegroundService.serviceState.value
        assertTrue("Service should be marked paused", state.isPaused)
        assertFalse("WakeLock should be released on pause", service.isWakeLockHeld())

        // 2.5 Resume Action
        val resumeIntent = Intent(context, NovelForegroundService::class.java).apply {
            action = NotificationHelper.ACTION_RESUME
        }
        service.onStartCommand(resumeIntent, 0, 3)
        state = NovelForegroundService.serviceState.value
        assertFalse("Service should be resumed", state.isPaused)
        assertTrue("WakeLock should be acquired on resume", service.isWakeLockHeld())

        // 2.6 Intent Action Routing: Switch to Audio Synthesis
        val audioIntent = Intent(context, NovelForegroundService::class.java).apply {
            action = NotificationHelper.ACTION_START_AUDIO
            putExtra(NotificationHelper.EXTRA_CURRENT_CHAPTER, 3)
            putExtra(NotificationHelper.EXTRA_CURRENT_SEGMENT, 12)
            putExtra(NotificationHelper.EXTRA_TOTAL_SEGMENTS, 28)
            putExtra(NotificationHelper.EXTRA_RTF, 0.85f)
        }
        service.onStartCommand(audioIntent, 0, 4)
        state = NovelForegroundService.serviceState.value
        assertEquals(ServiceMode.AUDIO_SYNTHESIS, state.mode)
        assertEquals(12, state.currentSegment)
        assertEquals(28, state.totalSegments)
        assertEquals(0.85f, state.rtf, 0.001f)

        // Update audio progress
        service.updateAudioProgress(3, 13, 28, 0.82f)
        state = NovelForegroundService.serviceState.value
        assertEquals(13, state.currentSegment)
        assertEquals(0.82f, state.rtf, 0.001f)

        // 2.7 Cancel Action
        val cancelIntent = Intent(context, NovelForegroundService::class.java).apply {
            action = NotificationHelper.ACTION_CANCEL
        }
        service.onStartCommand(cancelIntent, 0, 5)
        state = NovelForegroundService.serviceState.value
        assertFalse("Service should not be running after cancel", state.isRunning)
        assertFalse("WakeLock must be released on cancel", service.isWakeLockHeld())

        serviceController.destroy()
        assertFalse("WakeLock must not be held after destroy", service.isWakeLockHeld())

        // =========================================================================
        // SECTION 3: 40-Column WaveformSampler & Normalization
        // =========================================================================
        val barCount = 40

        // 3.1 Empty PCM
        val emptyBars = WaveformSampler.samplePcm16(ShortArray(0), barCount)
        assertEquals(barCount, emptyBars.size)
        assertTrue("Empty PCM must produce zero bars", emptyBars.all { it == 0f })

        // 3.2 Small PCM (< barCount)
        val smallPcm = ShortArray(15) { (it * 2000).toShort() }
        val smallBars = WaveformSampler.samplePcm16(smallPcm, barCount)
        assertEquals(barCount, smallBars.size)
        assertTrue("Small PCM bars must be in [0.0, 1.0]", smallBars.all { it in 0f..1f })

        // 3.3 Synthesized 48kHz Sinusoidal PCM (1 second = 48,000 samples)
        val sampleRate = 48000
        val numSamples = 48000
        val sinePcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val envelope = sin(PI * i / numSamples)
            val wave = sin(2.0 * PI * 440.0 * i / sampleRate)
            sinePcm[i] = (envelope * wave * 30000.0).toInt().toShort()
        }

        // RMS Downsampling
        val rmsBars = WaveformSampler.samplePcm16(sinePcm, barCount, useRms = true, normalizeToPeak = true)
        assertEquals(barCount, rmsBars.size)
        assertTrue("RMS bars must be in [0.0, 1.0]", rmsBars.all { it in 0f..1f })
        assertEquals("Normalized RMS peak bar must reach 1.0f", 1.0f, rmsBars.maxOrNull() ?: 0f, 0.001f)
        assertTrue("Middle bars must have higher amplitude than end bars due to envelope", rmsBars[barCount / 2] > rmsBars[0])

        // Peak Downsampling
        val peakBars = WaveformSampler.samplePcm16(sinePcm, barCount, useRms = false, normalizeToPeak = true)
        assertEquals(barCount, peakBars.size)
        assertTrue("Peak bars must be in [0.0, 1.0]", peakBars.all { it in 0f..1f })
        assertEquals("Normalized peak bar must reach 1.0f", 1.0f, peakBars.maxOrNull() ?: 0f, 0.001f)

        // Float PCM
        val floatPcm = FloatArray(numSamples) { sinePcm[it] / 32767f }
        val floatBars = WaveformSampler.sampleFloatPcm(floatPcm, barCount)
        assertEquals(barCount, floatBars.size)
        assertTrue("Float bars must be in [0.0, 1.0]", floatBars.all { it in 0f..1f })

        // WAV Bytes and File Downsampling with Caching
        val wavBytes = WavWriter.pcm16ToWav(sinePcm, sampleRate, 1)
        val wavBars = WaveformSampler.sampleWavBytes(wavBytes, barCount)
        assertEquals(barCount, wavBars.size)
        assertTrue("WAV byte bars must be in [0.0, 1.0]", wavBars.all { it in 0f..1f })

        val tempWavFile = File(context.cacheDir, "test_track_sample.wav")
        try {
            tempWavFile.writeBytes(wavBytes)
            val fileBars = WaveformSampler.sampleWavFile(tempWavFile, barCount)
            assertEquals(barCount, fileBars.size)
            assertArrayEquals("File bars must match byte bars", wavBars, fileBars, 0.001f)

            // Verify cache hit
            val cached = WaveformSampler.sampleWavFile(tempWavFile, barCount)
            assertTrue("Cached instance must match", cached.contentEquals(fileBars))
        } finally {
            tempWavFile.delete()
        }

        // =========================================================================
        // SECTION 4: PlaybackProgressTracker & Time Formatting
        // =========================================================================
        assertEquals("00:00", PlayerState.formatTime(0L))
        assertEquals("00:15", PlayerState.formatTime(15000L))
        assertEquals("01:15", PlayerState.formatTime(75000L))
        assertEquals("10:00", PlayerState.formatTime(600000L))
        assertEquals("00:00", PlayerState.formatTime(-1000L))

        assertEquals(0, PlaybackProgressTracker.calculateActiveBarIndex(0L, 60000L, 40))
        assertEquals(20, PlaybackProgressTracker.calculateActiveBarIndex(30000L, 60000L, 40))
        assertEquals(39, PlaybackProgressTracker.calculateActiveBarIndex(60000L, 60000L, 40))
        assertEquals(0.5f, PlaybackProgressTracker.calculateProgress(30000L, 60000L), 0.0001f)
        assertEquals(45000L, PlaybackProgressTracker.calculateSeekPosition(0.75f, 60000L))

        // Reactive tracker testing
        val progressTracker = PlaybackProgressTracker(scope = this, barCount = 40, pollingIntervalMs = 50L)
        progressTracker.update(15000L, 60000L)
        assertEquals(0.25f, progressTracker.progress.value, 0.001f)
        assertEquals(10, progressTracker.activeBarIndex.value)
        progressTracker.reset()
        assertEquals(0f, progressTracker.progress.value, 0.001f)
        assertEquals(0, progressTracker.activeBarIndex.value)

        // =========================================================================
        // SECTION 5: Primary AudioPlayerManager (MediaSession & State Transitions)
        // =========================================================================
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val primaryAdapter = FakeMediaPlayerAdapter().apply { durationInternal = 60000 }
        val primaryAudioPlayer = AudioPlayerManager(
            coroutineScope = testScope,
            playerAdapter = primaryAdapter
        )

        // 5.1 Initial state
        assertEquals(PlaybackStatus.IDLE, primaryAudioPlayer.playerState.value.status)

        // 5.2 Load track with MediaSession metadata
        val loadSuccess = primaryAudioPlayer.load(
            filePath = "/storage/audiobooks/ch01_sample.wav",
            title = "Dấu Chân Người Lính",
            chapterTitle = "Chương 1: Mở Đầu"
        )
        assertTrue("Load must succeed", loadSuccess)
        var pState = primaryAudioPlayer.playerState.value
        assertEquals(PlaybackStatus.PREPARED, pState.status)
        assertEquals(60000L, pState.durationMs)
        assertEquals(0L, pState.currentPositionMs)
        assertEquals("Dấu Chân Người Lính", pState.metadata.title)
        assertEquals("Chương 1: Mở Đầu", pState.metadata.chapterTitle)
        assertEquals("00:00 / 01:00", pState.formattedTimeDisplay)

        // 5.3 Playback start and 50ms polling loop verification
        val playSuccess = primaryAudioPlayer.play()
        assertTrue("Play must succeed", playSuccess)
        pState = primaryAudioPlayer.playerState.value
        assertEquals(PlaybackStatus.PLAYING, pState.status)
        assertTrue(primaryAdapter.isPlaying())

        // Simulate audio advancing to 10,000ms
        primaryAdapter.currentPositionInternal = 10000
        testScheduler.advanceTimeBy(100) // Trigger 50ms polling loop
        assertEquals(10000L, primaryAudioPlayer.playerState.value.currentPositionMs)
        assertEquals(10000f / 60000f, primaryAudioPlayer.playerState.value.progress, 0.001f)

        // 5.4 Precision Scrubbing
        primaryAudioPlayer.seekTo(35000L)
        assertEquals(35000, primaryAdapter.currentPositionInternal)
        assertEquals(35000L, primaryAudioPlayer.playerState.value.currentPositionMs)

        // Seek to fraction (0.5 = 30000ms)
        primaryAudioPlayer.seekToFraction(0.5f)
        assertEquals(30000L, primaryAudioPlayer.playerState.value.currentPositionMs)

        // 5.5 Pause & Resume
        assertTrue(primaryAudioPlayer.pause())
        pState = primaryAudioPlayer.playerState.value
        assertEquals(PlaybackStatus.PAUSED, pState.status)
        assertFalse(primaryAdapter.isPlaying())

        assertTrue(primaryAudioPlayer.resume())
        pState = primaryAudioPlayer.playerState.value
        assertEquals(PlaybackStatus.PLAYING, pState.status)
        assertTrue(primaryAdapter.isPlaying())

        // 5.6 Metadata update while playing
        primaryAudioPlayer.updateMetadata(chapterTitle = "Chương 1: Khúc Tráng Ca")
        assertEquals("Chương 1: Khúc Tráng Ca", primaryAudioPlayer.playerState.value.metadata.chapterTitle)

        // 5.7 Replay
        assertTrue(primaryAudioPlayer.replay())
        assertEquals(0L, primaryAudioPlayer.playerState.value.currentPositionMs)

        // 5.8 Completion Event
        primaryAdapter.completionCallback?.invoke()
        pState = primaryAudioPlayer.playerState.value
        assertEquals(PlaybackStatus.COMPLETED, pState.status)
        assertEquals(60000L, pState.currentPositionMs)

        // Playing from COMPLETED restarts at 0
        primaryAudioPlayer.play()
        assertEquals(PlaybackStatus.PLAYING, primaryAudioPlayer.playerState.value.status)
        assertEquals(0L, primaryAudioPlayer.playerState.value.currentPositionMs)

        // 5.9 Error Handling
        primaryAdapter.errorCallback?.invoke(1, -1004)
        pState = primaryAudioPlayer.playerState.value
        assertEquals(PlaybackStatus.ERROR, pState.status)
        assertNotNull(pState.errorMessage)

        // Load error handling
        val failLoad = primaryAudioPlayer.load("invalid_path_fail.wav")
        assertFalse(failLoad)
        assertEquals(PlaybackStatus.ERROR, primaryAudioPlayer.playerState.value.status)

        // 5.10 Release
        primaryAudioPlayer.release()
        assertEquals(PlaybackStatus.IDLE, primaryAudioPlayer.playerState.value.status)
        assertTrue(primaryAdapter.isReleased)

        // =========================================================================
        // SECTION 6: VoiceSamplePlayer & Non-Interference with Primary Player
        // =========================================================================
        val primaryAdapter2 = FakeMediaPlayerAdapter().apply { durationInternal = 40000 }
        val mainPlayer = AudioPlayerManager(coroutineScope = testScope, playerAdapter = primaryAdapter2)
        mainPlayer.load("/storage/main_novel.wav", "Main Novel", "Chương 5")
        mainPlayer.play()
        assertTrue("Main player must be playing", mainPlayer.playerState.value.isPlaying)

        // Voice Preview Player initialized with its independent adapter
        val voiceAdapter = FakeMediaPlayerAdapter().apply { durationInternal = 5000 }
        val voiceSamplePlayer = VoiceSamplePlayer(playerAdapter = voiceAdapter)

        // 6.1 Play Voice Sample 1
        val sample1Success = voiceSamplePlayer.playVoiceSample("voice_hn_nam_01", "/storage/voices/voice1.wav")
        assertTrue("Voice preview 1 must play successfully", sample1Success)
        assertTrue("Voice player must be playing", voiceSamplePlayer.state.value.isPlaying)
        assertEquals("voice_hn_nam_01", voiceSamplePlayer.state.value.currentVoiceId)

        // Verify primary player was NOT interrupted or modified
        assertTrue("Primary player must still be PLAYING without interference", mainPlayer.playerState.value.isPlaying)
        assertEquals("Main Novel", mainPlayer.playerState.value.metadata.title)
        assertEquals("/storage/main_novel.wav", mainPlayer.playerState.value.audioFilePath)

        // 6.2 Switch Voice: Selecting a new voice automatically stops previous preview
        val sample2Success = voiceSamplePlayer.playVoiceSample("voice_sg_nu_02", "/storage/voices/voice2.wav")
        assertTrue("Voice preview 2 must play successfully", sample2Success)
        assertEquals("voice_sg_nu_02", voiceSamplePlayer.state.value.currentVoiceId)
        assertTrue(voiceSamplePlayer.state.value.isPlaying)

        // Primary player STILL unaffected
        assertTrue("Primary player must remain untouched", mainPlayer.playerState.value.isPlaying)

        // 6.3 Voice Sample Completion
        voiceAdapter.completionCallback?.invoke()
        assertFalse("Voice player should stop on completion", voiceSamplePlayer.state.value.isPlaying)
        assertEquals(null, voiceSamplePlayer.state.value.currentVoiceId)

        // 6.4 Stop & Release Voice Sample Player
        voiceSamplePlayer.playVoiceSample("voice_hue_nam_03", "/storage/voices/voice3.wav")
        assertTrue(voiceSamplePlayer.state.value.isPlaying)
        voiceSamplePlayer.stopVoiceSample()
        assertFalse(voiceSamplePlayer.state.value.isPlaying)

        voiceSamplePlayer.release()
        assertTrue(voiceAdapter.isReleased)

        // Finally stop and release primary player
        mainPlayer.release()
        assertTrue(primaryAdapter2.isReleased)
    }
}
