package com.ainovel.audiobook.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

import com.ainovel.audiobook.data.repository.NovelRepository

enum class ReaderThemeMode {
    DAY, NIGHT, SEPIA
}

data class ReaderUiState(
    val novelTitle: String = "The Chronicles of Whisper",
    val chapterTitle: String = "Chương 4: Trước Khi Thành Phố Kịp Nhắm Mắt",
    val content: String = """
        Lưới số rung bóp vũ thẩm thấu ái ngại se bạn con nhiệt. Ánh đèn neon tím nhạt phản chiếu trên mặt đường ướt sau cơn mưa tầm tã. Những tòa cao ốc chọc trời sừng sững như những gã khổng lồ bằng thép và kính đang say ngủ.

        Thành phố này không bao giờ thực sự nhắm mắt. Dưới những tầng ngầm của khu phố cổ, các cỗ máy tổng hợp vẫn đang rì rầm hoạt động, truyền đi từng luồng dữ liệu số mã hóa của những ký ức nhân tạo bị đánh cắp.

        Anh bước nhanh qua ngõ nhỏ, chiếc áo khoác đen phất phới theo làn gió lạnh. Trong túi áo, thiết bị lưu trữ tí hon rung lên từng nhịp nhẹ – dấu hiệu cho thấy việc giải mã sắp hoàn tất. Chỉ còn ba mươi giây nữa, sự thật đằng sau dự án 'Whisper' sẽ hoàn toàn lộ diện.
    """.trimIndent(),
    val fontSizeSp: Float = 16f,
    val lineSpacingMultiplier: Float = 1.6f,
    val themeMode: ReaderThemeMode = ReaderThemeMode.NIGHT,
    val isGeneratingAudio: Boolean = false
)

class ReaderViewModel(
    private val novelRepository: NovelRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun updateFontSize(sizeSp: Float) {
        val clamped = sizeSp.coerceIn(12f, 28f)
        _uiState.update { it.copy(fontSizeSp = clamped) }
    }

    fun updateLineSpacing(multiplier: Float) {
        val clamped = multiplier.coerceIn(1.2f, 2.4f)
        _uiState.update { it.copy(lineSpacingMultiplier = clamped) }
    }

    fun setThemeMode(mode: ReaderThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setContent(title: String, chapter: String, body: String) {
        _uiState.update {
            it.copy(
                novelTitle = title,
                chapterTitle = chapter,
                content = body
            )
        }
    }

    fun triggerAudiobookGeneration(onReady: () -> Unit) {
        _uiState.update { it.copy(isGeneratingAudio = true) }
        onReady()
    }
}
