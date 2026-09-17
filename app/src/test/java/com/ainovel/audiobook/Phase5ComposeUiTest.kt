package com.ainovel.audiobook

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.ainovel.audiobook.domain.model.NovelTemplate
import com.ainovel.audiobook.player.PlaybackStatus
import com.ainovel.audiobook.player.WaveformSampler
import com.ainovel.audiobook.tts.engine.VoicePreset
import com.ainovel.audiobook.tts.engine.VoicePresets
import com.ainovel.audiobook.ui.navigation.Screen
import com.ainovel.audiobook.ui.theme.Typography
import com.ainovel.audiobook.ui.viewmodel.AudioStudioViewModel
import com.ainovel.audiobook.ui.viewmodel.DashboardViewModel
import com.ainovel.audiobook.ui.viewmodel.GeneratorViewModel
import com.ainovel.audiobook.ui.viewmodel.ReaderThemeMode
import com.ainovel.audiobook.ui.viewmodel.ReaderViewModel
import com.ainovel.audiobook.ui.viewmodel.SettingsViewModel
import com.ainovel.audiobook.ui.viewmodel.VoicePickerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Phase 05 Comprehensive Single Verification Test:
 * Mockup-Driven Compose UI, Zero Text Clipping, Voice Picker Studio, and Bilingual Localization.
 *
 * Rule: Single verification test for Phase 05.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase5ComposeUiTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // ------------------------------------------------------------------------
    // 1. Zero Text Clipping & Typography Metric Tests
    // ------------------------------------------------------------------------
    @Test
    fun testTypographyFontMetricsEliminateDescenderClipping() {
        val styles = listOf(
            "displayLarge" to Typography.displayLarge,
            "displayMedium" to Typography.displayMedium,
            "headlineLarge" to Typography.headlineLarge,
            "headlineMedium" to Typography.headlineMedium,
            "titleLarge" to Typography.titleLarge,
            "titleMedium" to Typography.titleMedium,
            "titleSmall" to Typography.titleSmall,
            "bodyLarge" to Typography.bodyLarge,
            "bodyMedium" to Typography.bodyMedium,
            "bodySmall" to Typography.bodySmall,
            "labelLarge" to Typography.labelLarge,
            "labelMedium" to Typography.labelMedium,
            "labelSmall" to Typography.labelSmall
        )

        for ((name, style) in styles) {
            val fontSize = style.fontSize.value
            val lineHeight = style.lineHeight.value

            assertTrue(
                "Typography style '$name' must specify positive fontSize",
                fontSize > 0f
            )
            assertTrue(
                "Typography style '$name' must specify lineHeight greater than fontSize to eliminate descender clipping (g, y, p, q, j and Vietnamese ệ, ộ, ử)",
                lineHeight >= fontSize * 1.25f
            )
        }
    }

    @Test
    fun testZeroTextClippingUnderHighFontScaling() {
        val standardDensity = Density(density = 2.0f, fontScale = 1.0f)
        val accessibility15xDensity = Density(density = 2.0f, fontScale = 1.5f)
        val accessibility20xDensity = Density(density = 2.0f, fontScale = 2.0f)

        val baseFontSize = Typography.bodyLarge.fontSize
        val baseLineHeight = Typography.bodyLarge.lineHeight

        val h10 = with(standardDensity) {
            val h = baseLineHeight.toPx()
            val f = baseFontSize.toPx()
            assertTrue("LineHeight must exceed FontSize at 1.0x", h > f)
            h
        }

        val h15 = with(accessibility15xDensity) {
            val h = baseLineHeight.toPx()
            val f = baseFontSize.toPx()
            assertTrue("LineHeight must scale dynamically without collapse at 1.5x", h > f)
            assertTrue("1.5x fontScale must enlarge vertical height over 1.0x", h > h10)
            h
        }

        val h20 = with(accessibility20xDensity) {
            val h = baseLineHeight.toPx()
            val f = baseFontSize.toPx()
            assertTrue("LineHeight must scale dynamically without collapse at 2.0x", h > f)
            assertTrue("2.0x fontScale must enlarge vertical height over 1.5x", h > h15)
            h
        }

        assertTrue("LineHeight must scale monotonically across 1.0x -> 1.5x -> 2.0x", h20 > h15 && h15 > h10)
    }

    // ------------------------------------------------------------------------
    // 2. WaveformVisualizer & 40-Bar Amplitude Tests
    // ------------------------------------------------------------------------
    @Test
    fun testWaveformVisualizerBarCountAndSamplingIntegrity() {
        val emptyAmplitudes = FloatArray(0)
        val sampleAmps = WaveformSampler.sampleFloatPcm(emptyAmplitudes, 40)
        assertEquals("Waveform sampler must produce exactly 40 bars for empty input", 40, sampleAmps.size)

        val sineWave = FloatArray(48000) { i ->
            kotlin.math.sin(i * 0.05).toFloat()
        }
        val bars40 = WaveformSampler.sampleFloatPcm(sineWave, barCount = 40)
        assertEquals("Waveform sampler must produce exactly 40 bars", 40, bars40.size)

        for (i in bars40.indices) {
            assertTrue("Amplitude at bar $i must be in [0.0, 1.0]", bars40[i] in 0f..1f)
        }

        val nonNormalized = FloatArray(40) { 0.1f }
        val normalized = WaveformSampler.sampleFloatPcm(nonNormalized, 40, normalizeToPeak = true)
        assertEquals(40, normalized.size)
        assertEquals("Peak normalization should scale peak to 1.0", 1.0f, normalized.maxOrNull() ?: 0f, 0.001f)
    }

    // ------------------------------------------------------------------------
    // 3. Voice Picker Studio & Instant Sample Preview Tests
    // ------------------------------------------------------------------------
    @Test
    fun testVoicePresetsCatalogAndRegionGrouping() {
        val presets = VoicePresets.getVoicePresets()
        assertTrue("Curated presets must have at least 8 default voices", presets.size >= 8)

        val northVoices = presets.filter { it.region == "Bắc" }
        val centralVoices = presets.filter { it.region == "Trung" }
        val southVoices = presets.filter { it.region == "Nam" }

        assertTrue("Must have Northern region voices", northVoices.isNotEmpty())
        assertTrue("Must have Central region voices", centralVoices.isNotEmpty())
        assertTrue("Must have Southern region voices", southVoices.isNotEmpty())

        val femaleVoices = presets.filter { it.genderDisplay == "Nữ" }
        val maleVoices = presets.filter { it.genderDisplay == "Nam" }

        assertTrue("Must have Female voices", femaleVoices.isNotEmpty())
        assertTrue("Must have Male voices", maleVoices.isNotEmpty())

        val defaultVoice = VoicePresets.getVoice(VoicePresets.defaultVoiceName)
        assertNotNull("Default voice must be resolvable", defaultVoice)
        assertEquals("Ngọc Huyền", defaultVoice?.name)
    }

    @Test
    fun testVoicePickerViewModelSamplePreviewAndSelection() {
        val viewModel = VoicePickerViewModel()
        val initialState = viewModel.uiState.value

        assertNull("Initial currently playing voice should be null", initialState.currentlyPlayingVoiceId)
        assertFalse("Initial sample isPlaying should be false", initialState.isPlaying)

        // Trigger sample preview for "Ly"
        viewModel.previewSample("Ly")
        val playingState = viewModel.uiState.value
        assertEquals("Ly", playingState.currentlyPlayingVoiceId)
        assertTrue(playingState.isPlaying)

        // Toggle again on same voice -> should stop
        viewModel.previewSample("Ly")
        val stoppedState = viewModel.uiState.value
        assertNull(stoppedState.currentlyPlayingVoiceId)
        assertFalse(stoppedState.isPlaying)

        // Switch to new voice selection
        val voiceBinh = VoicePresets.getVoice("Bình")!!
        viewModel.selectVoice(voiceBinh)
        val selectedState = viewModel.uiState.value
        assertEquals("Bình", selectedState.selectedVoiceName)
        assertFalse(selectedState.isPlaying)
    }

    // ------------------------------------------------------------------------
    // 4. Bilingual Localization & 100% Key Parity Tests
    // ------------------------------------------------------------------------
    @Test
    fun testBilingualStringResourceResolution() {
        val viConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale("vi"))
        }
        val viContext = context.createConfigurationContext(viConfig)

        val enConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale("en"))
        }
        val enContext = context.createConfigurationContext(enConfig)

        // App Name
        assertEquals("AI Novelist & Audiobook", enContext.getString(R.string.app_name))
        assertEquals("Tiểu Thuyết AI & Sách Nói", viContext.getString(R.string.app_name))

        // Navigation
        assertEquals("Studio", enContext.getString(R.string.nav_dashboard))
        assertEquals("Tổng quan", viContext.getString(R.string.nav_dashboard))

        assertEquals("Outline", enContext.getString(R.string.nav_outline))
        assertEquals("Đề cương", viContext.getString(R.string.nav_outline))

        assertEquals("Reader", enContext.getString(R.string.nav_reader))
        assertEquals("Đọc sách", viContext.getString(R.string.nav_reader))

        assertEquals("Audio Studio", enContext.getString(R.string.nav_audio_studio))
        assertEquals("Phòng thu âm", viContext.getString(R.string.nav_audio_studio))

        // Voice Picker Studio
        assertEquals("Preview", enContext.getString(R.string.preview_sample))
        assertEquals("Nghe Thử", viContext.getString(R.string.preview_sample))

        assertEquals("Stop", enContext.getString(R.string.stop_sample))
        assertEquals("Dừng", viContext.getString(R.string.stop_sample))

        assertEquals("Export WAV", enContext.getString(R.string.export_wav))
        assertEquals("Xuất file WAV", viContext.getString(R.string.export_wav))
    }

    @Test
    fun testStringResourceXmlParityBetweenEnAndVi() {
        val baseResDir = File("src/main/res")
        val enStringsFile = File(baseResDir, "values/strings.xml")
        val viStringsFile = File(baseResDir, "values-vi/strings.xml")

        assertTrue("English strings.xml must exist", enStringsFile.exists())
        assertTrue("Vietnamese strings.xml must exist", viStringsFile.exists())

        fun extractStringKeys(file: File): Set<String> {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(file)
            val nodeList = doc.getElementsByTagName("string")
            val keys = mutableSetOf<String>()
            for (i in 0 until nodeList.length) {
                val elem = nodeList.item(i) as Element
                keys.add(elem.getAttribute("name"))
            }
            return keys
        }

        val enKeys = extractStringKeys(enStringsFile)
        val viKeys = extractStringKeys(viStringsFile)

        assertTrue("English strings must contain at least 25 keys", enKeys.size >= 25)
        assertEquals(
            "English and Vietnamese strings must have 100% key parity without missing translations",
            enKeys,
            viKeys
        )
    }

    // ------------------------------------------------------------------------
    // 5. Navigation Graph & Screen ViewModel Integration Flow Tests
    // ------------------------------------------------------------------------
    @Test
    fun testNavigationGraphRouteHierarchy() {
        assertEquals("dashboard", Screen.Dashboard.route)
        assertEquals("outline", Screen.Outline.route)
        assertEquals("live_console", Screen.LiveConsole.route)
        assertEquals("reader/{novelId}", Screen.Reader.route)
        assertEquals("reader/novel_123", Screen.Reader.createRoute("novel_123"))
        assertEquals("audio_studio/{novelId}", Screen.AudioStudio.route)
        assertEquals("audio_studio/novel_123", Screen.AudioStudio.createRoute("novel_123"))
        assertEquals("settings", Screen.Settings.route)
    }

    @Test
    fun testDashboardViewModelNovelManagement() {
        val dashboardVm = DashboardViewModel()
        val initialCount = dashboardVm.uiState.value.novels.size
        assertTrue("Initial sample novels must be loaded", initialCount >= 2)

        dashboardVm.createNovel(
            title = "Dấu Ấn Không Gian",
            topic = "Du hành thời không tìm lại văn minh đã mất",
            genre = "hard_sci_fi"
        )

        val updatedNovels = dashboardVm.uiState.value.novels
        assertEquals(initialCount + 1, updatedNovels.size)
        assertEquals("Dấu Ấn Không Gian", updatedNovels.first().title)

        val novelIdToDelete = updatedNovels.first().id
        dashboardVm.deleteNovel(novelIdToDelete)
        assertEquals(initialCount, dashboardVm.uiState.value.novels.size)
    }

    @Test
    fun testGeneratorViewModelOutlineAndChapterReordering() {
        val generatorVm = GeneratorViewModel()
        assertEquals(4, generatorVm.uiState.value.chapters.size)

        // Add chapter
        generatorVm.addChapter("Chương 5: Kết Thúc Hay Khởi Đầu")
        assertEquals(5, generatorVm.uiState.value.chapters.size)
        assertEquals("Chương 5: Kết Thúc Hay Khởi Đầu", generatorVm.uiState.value.chapters.last().title)

        // Reorder: move last chapter to top
        generatorVm.moveChapter(4, 0)
        assertEquals("Chương 5: Kết Thúc Hay Khởi Đầu", generatorVm.uiState.value.chapters[0].title)
        assertEquals(1, generatorVm.uiState.value.chapters[0].index)

        // Template selector
        generatorVm.selectTemplate(NovelTemplate.HARD_SCI_FI)
        assertEquals("hard_sci_fi", generatorVm.uiState.value.selectedTemplate.id)

        // Synopsis cap verification (max 5,000 characters)
        val shortSynopsis = "A short premise"
        generatorVm.updateSynopsis(shortSynopsis)
        assertEquals(shortSynopsis, generatorVm.uiState.value.synopsis)

        val over5kSynopsis = "a".repeat(5001)
        generatorVm.updateSynopsis(over5kSynopsis)
        assertNotEquals("Synopsis must not exceed 5000 characters cap", over5kSynopsis, generatorVm.uiState.value.synopsis)
    }

    @Test
    fun testReaderViewModelCustomizationAndAudioTrigger() {
        val readerVm = ReaderViewModel()
        assertEquals(16f, readerVm.uiState.value.fontSizeSp, 0.01f)
        assertEquals(ReaderThemeMode.NIGHT, readerVm.uiState.value.themeMode)

        readerVm.updateFontSize(22f)
        assertEquals(22f, readerVm.uiState.value.fontSizeSp, 0.01f)

        readerVm.setThemeMode(ReaderThemeMode.SEPIA)
        assertEquals(ReaderThemeMode.SEPIA, readerVm.uiState.value.themeMode)

        var audioTriggered = false
        readerVm.triggerAudiobookGeneration {
            audioTriggered = true
        }
        assertTrue("Trigger audiobook generation callback executed", audioTriggered)
        assertTrue(readerVm.uiState.value.isGeneratingAudio)
    }

    @Test
    fun testAudioStudioViewModelPlayerControlsAndExport() {
        val studioVm = AudioStudioViewModel()
        val state = studioVm.uiState.value
        assertEquals(PlaybackStatus.PLAYING, state.playerState.status)
        assertEquals(40, state.waveformAmplitudes.size)

        // Scrubber seeking
        studioVm.seekTo(0.5f)
        val halfDuration = state.playerState.durationMs / 2
        assertEquals(halfDuration, studioVm.uiState.value.playerState.currentPositionMs)

        // Rewind 10s
        studioVm.rewind10s()
        assertTrue(studioVm.uiState.value.playerState.currentPositionMs <= halfDuration)

        // Forward 10s
        studioVm.forward10s()
        assertEquals(halfDuration, studioVm.uiState.value.playerState.currentPositionMs)

        // Speed cycling
        assertEquals(1.0f, studioVm.uiState.value.playbackSpeed, 0.01f)
        studioVm.cycleSpeed()
        assertEquals(1.25f, studioVm.uiState.value.playbackSpeed, 0.01f)
        studioVm.cycleSpeed()
        assertEquals(1.5f, studioVm.uiState.value.playbackSpeed, 0.01f)
        studioVm.cycleSpeed()
        assertEquals(2.0f, studioVm.uiState.value.playbackSpeed, 0.01f)
        studioVm.cycleSpeed()
        assertEquals(1.0f, studioVm.uiState.value.playbackSpeed, 0.01f)

        // Export Actions
        studioVm.exportWav()
        assertNotNull(studioVm.uiState.value.exportSuccessMessage)
        assertTrue(studioVm.uiState.value.exportSuccessMessage!!.contains("WAV"))

        studioVm.exportZip()
        assertTrue(studioVm.uiState.value.exportSuccessMessage!!.contains("ZIP"))
    }

    @Test
    fun testSettingsViewModelApiKeyMaskingAndLanguageSwitch() {
        val settingsVm = SettingsViewModel()
        assertEquals("vi", settingsVm.uiState.value.currentLanguage)

        // In-app language switcher
        settingsVm.setLanguage("en")
        assertEquals("en", settingsVm.uiState.value.currentLanguage)

        // Add raw API Key and verify masking
        val rawKey = "AIzaSyDxyz1234567890abcdefghijkl"
        settingsVm.addApiKey(rawKey)
        val addedKey = settingsVm.uiState.value.apiKeys.last()
        assertEquals("AIzaSy...ijkl", addedKey.maskedKey)
        assertFalse(addedKey.maskedKey.contains("1234567890"))

        // Model priority toggle
        val firstModelId = settingsVm.uiState.value.modelPriorities.first().modelId
        settingsVm.toggleModelEnabled(firstModelId)
        assertFalse(settingsVm.uiState.value.modelPriorities.first().isEnabled)
    }
}
