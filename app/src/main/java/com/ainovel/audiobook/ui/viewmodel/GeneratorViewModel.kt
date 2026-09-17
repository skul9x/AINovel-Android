package com.ainovel.audiobook.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.audiobook.data.local.entity.ChapterEntity
import com.ainovel.audiobook.data.repository.NovelRepository
import com.ainovel.audiobook.domain.engine.NovelGenerationEngine
import com.ainovel.audiobook.domain.engine.RotationManager
import com.ainovel.audiobook.domain.model.NovelTemplate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class ChapterOutlineItem(
    val index: Int,
    val title: String,
    val summary: String = ""
)

data class GeneratorUiState(
    val novelTitle: String = "Trước Khi Thành Phố Kịp Nhắm Mắt",
    val synopsis: String = "Trong ánh đèn neon mờ ảo của đô thị tương lai, một vụ án mạng kỳ lạ đã mở ra bức màn bí mật về ký ức nhân tạo bị đánh cắp.",
    val selectedTemplate: NovelTemplate = NovelTemplate.WEB_NOVEL,
    val chapters: List<ChapterOutlineItem> = listOf(
        ChapterOutlineItem(1, "Chương 1: Ánh Đèn Cyberpunk"),
        ChapterOutlineItem(2, "Chương 2: Kẻ Đánh Cắp Ký Ức"),
        ChapterOutlineItem(3, "Chương 3: Rung Chuông Báo Động"),
        ChapterOutlineItem(4, "Chương 4: Trước Khi Thành Phố Kịp Nhắm Mắt")
    ),
    val isGeneratingOutline: Boolean = false,
    val isStreaming: Boolean = false,
    val streamingContent: String = "Lưới số rung bóp vũ thẩm thấu ái ngại se bạn con nhiệt. Th anh chỉ nên đóng ngọn lửa nãy nhìn đồn rịt mỗi có đề trên trong thứ ban tôi tặng Trước Khi Thành Phố Kịp nhắm mắt chương nếu.\n\nThế có tin tưởng rã số vàm trí trọc giát dừng tận đánh. Nến thanh ân bán cúc nói ai năng người nặc, hấp lại, lên thương đợi cũng người đón, những thì an trước hề cũng quan không nước nho tượng ciph thành lòng thế dẫn đợi aiã.",
    val tokensPerSecond: Int = 52,
    val progressPercent: Int = 62,
    val activeModel: String = "Gemini 3.8 Flash",
    val maxSynopsisLength: Int = 5000,
    val activeNovelId: String? = null,
    val errorMessage: String? = null
)

class GeneratorViewModel(
    private val engine: NovelGenerationEngine? = null,
    private val repository: NovelRepository? = null,
    private val rotationManager: RotationManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeneratorUiState())
    val uiState: StateFlow<GeneratorUiState> = _uiState.asStateFlow()

    private var streamingJob: Job? = null

    init {
        if (engine != null && repository != null) {
            _uiState.value = GeneratorUiState(
                novelTitle = "",
                synopsis = "",
                chapters = emptyList(),
                streamingContent = "",
                isStreaming = false,
                tokensPerSecond = 0,
                progressPercent = 0
            )
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(novelTitle = title) }
    }

    fun updateSynopsis(newSynopsis: String) {
        if (newSynopsis.length <= _uiState.value.maxSynopsisLength) {
            _uiState.update { it.copy(synopsis = newSynopsis) }
        }
    }

    fun selectTemplate(template: NovelTemplate) {
        _uiState.update { it.copy(selectedTemplate = template) }
    }

    fun addChapter(title: String) {
        val current = _uiState.value.chapters
        val newChapter = ChapterOutlineItem(
            index = current.size + 1,
            title = title.ifEmpty { "Chương ${current.size + 1}" }
        )
        _uiState.update { it.copy(chapters = current + newChapter) }
    }

    fun updateChapterTitle(index: Int, newTitle: String) {
        _uiState.update { state ->
            val updated = state.chapters.mapIndexed { idx, ch ->
                if (idx == index) ch.copy(title = newTitle) else ch
            }
            state.copy(chapters = updated)
        }
    }

    fun removeChapter(index: Int) {
        _uiState.update { state ->
            val updated = state.chapters.filterIndexed { idx, _ -> idx != index }
                .mapIndexed { idx, ch -> ch.copy(index = idx + 1) }
            state.copy(chapters = updated)
        }
    }

    fun moveChapter(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        _uiState.update { state ->
            val list = state.chapters.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                val reindexed = list.mapIndexed { idx, ch -> ch.copy(index = idx + 1) }
                state.copy(chapters = reindexed)
            } else {
                state
            }
        }
    }

    fun generateOutline() {
        if (engine == null || repository == null) {
            // Preview / Test Fallback
            viewModelScope.launch {
                _uiState.update { it.copy(isGeneratingOutline = true, errorMessage = null) }
                delay(300)
                val generatedChapters = listOf(
                    ChapterOutlineItem(1, "Chương 1: Điềm Báo Trong Bóng Đêm"),
                    ChapterOutlineItem(2, "Chương 2: Dấu Vết Bị Lãng Quên"),
                    ChapterOutlineItem(3, "Chương 3: Cơn Bão Đang Tới"),
                    ChapterOutlineItem(4, "Chương 4: Điểm Nút Định Mệnh")
                )
                _uiState.update {
                    it.copy(
                        isGeneratingOutline = false,
                        chapters = generatedChapters
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingOutline = true, errorMessage = null) }

            // Check if rotation candidate is available
            val candidate = rotationManager?.electCandidate()
            if (candidate == null) {
                _uiState.update {
                    it.copy(
                        isGeneratingOutline = false,
                        errorMessage = "Chưa có Google Gemini API Key khả dụng. Vui lòng vào màn hình Cài đặt để thêm API Key!"
                    )
                }
                return@launch
            }

            try {
                var novelId = _uiState.value.activeNovelId
                if (novelId.isNullOrBlank()) {
                    val novel = repository.createNovel(
                        title = _uiState.value.novelTitle.ifBlank { "Tác Phẩm Mới" },
                        topic = _uiState.value.synopsis,
                        genre = _uiState.value.selectedTemplate.id,
                        language = "vi"
                    )
                    novelId = novel.id
                    _uiState.update { it.copy(activeNovelId = novelId) }
                }

                val result = engine.generateOutline(
                    novelId = novelId,
                    topic = _uiState.value.synopsis.ifBlank { _uiState.value.novelTitle },
                    genre = _uiState.value.selectedTemplate.id,
                    totalChapters = _uiState.value.chapters.size.coerceAtLeast(4),
                    language = "vi"
                )

                if (result.isSuccess) {
                    val titles = repository.getChaptersList(novelId).map { ch ->
                        ChapterOutlineItem(
                            index = ch.chapterIndex,
                            title = ch.title
                        )
                    }

                    _uiState.update {
                        it.copy(
                            isGeneratingOutline = false,
                            chapters = if (titles.isNotEmpty()) titles else it.chapters,
                            activeModel = candidate.modelId
                        )
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    val errorMessage = ex?.message?.ifBlank { null }
                        ?: ex?.localizedMessage?.ifBlank { null }
                        ?: ex?.javaClass?.simpleName
                        ?: "Lỗi không xác định khi gọi Google API để sinh đề cương"
                    android.util.Log.e("AINovel_GenVM", "generateOutline error: $errorMessage", ex)
                    _uiState.update {
                        it.copy(
                            isGeneratingOutline = false,
                            errorMessage = errorMessage
                        )
                    }
                }
            } catch (e: Exception) {
                val errorText = e.message?.ifBlank { null }
                    ?: e.localizedMessage?.ifBlank { null }
                    ?: e.javaClass.simpleName
                android.util.Log.e("AINovel_GenVM", "generateOutline exception: $errorText", e)
                _uiState.update {
                    it.copy(
                        isGeneratingOutline = false,
                        errorMessage = "Lỗi kết nối: $errorText"
                    )
                }
            }
        }
    }

    fun startStreamingGeneration(chapterTitle: String) {
        streamingJob?.cancel()
        _uiState.update {
            it.copy(
                isStreaming = true,
                streamingContent = "",
                progressPercent = 0,
                errorMessage = null
            )
        }

        if (engine == null || repository == null) {
            // Preview / Test Fallback
            streamingJob = viewModelScope.launch {
                val sampleTokens = listOf(
                    "Trong ", "đêm ", "tối, ", "ánh ", "đèn ", "neon ", "nhấp ", "nháy ", "chiếu ", "rọi ",
                    "xuống ", "con ", "đường ", "ướt ", "sũng. ", "Thành ", "phố ", "vẫn ", "chìm ", "trong ",
                    "sự ", "yên ", "lặng ", "đáng ", "sợ ", "trước ", "khi ", "cơn ", "bão ", "bắt ", "đầu."
                )
                val sb = StringBuilder()
                for ((index, token) in sampleTokens.withIndex()) {
                    if (!isActive) break
                    sb.append(token)
                    val progress = ((index + 1).toFloat() / sampleTokens.size * 100).toInt()
                    _uiState.update {
                        it.copy(
                            streamingContent = sb.toString(),
                            progressPercent = progress
                        )
                    }
                    delay(120)
                }
                _uiState.update { it.copy(isStreaming = false) }
            }
            return
        }

        streamingJob = viewModelScope.launch {
            try {
                val candidate = rotationManager?.electCandidate()
                if (candidate == null) {
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            errorMessage = "Chưa có Google Gemini API Key khả dụng. Vui lòng vào Cài đặt để thêm key!"
                        )
                    }
                    return@launch
                }

                val novelId = _uiState.value.activeNovelId ?: run {
                    val novel = repository.createNovel(
                        title = _uiState.value.novelTitle,
                        topic = _uiState.value.synopsis,
                        genre = _uiState.value.selectedTemplate.id
                    )
                    _uiState.update { it.copy(activeNovelId = novel.id) }
                    novel.id
                }

                val chapters = repository.getChaptersList(novelId)
                val targetChapter = chapters.firstOrNull() ?: run {
                    val ch = ChapterEntity(
                        id = UUID.randomUUID().toString(),
                        novelId = novelId,
                        chapterIndex = 1,
                        title = chapterTitle,
                        status = "in_progress"
                    )
                    repository.saveChapter(ch)
                    ch
                }

                val startTime = System.currentTimeMillis()
                var tokenCount = 0
                val stringBuffer = StringBuilder()

                val result = engine.generateChapter(
                    novelId = novelId,
                    chapterIndex = targetChapter.chapterIndex
                ) { token ->
                    tokenCount++
                    stringBuffer.append(token)
                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000f).coerceAtLeast(0.1f)
                    val tps = (tokenCount / elapsedSec).toInt()

                    _uiState.update { state ->
                        state.copy(
                            streamingContent = stringBuffer.toString(),
                            tokensPerSecond = tps,
                            progressPercent = (stringBuffer.length / 40).coerceIn(1, 99),
                            activeModel = candidate.modelId
                        )
                    }
                }

                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            progressPercent = 100
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            errorMessage = result.exceptionOrNull()?.message ?: "Lỗi khi viết chương"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        errorMessage = "Lỗi kết nối LLM: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        _uiState.update { it.copy(isStreaming = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
