# 📖 AI Novelist & Audiobook Studio (Android)

> **Ứng dụng Sáng tác Tiểu thuyết, Giáo trình & Chế tác Sách nói AI Song ngữ (Việt - Anh) Đỉnh cao trên Nền tảng Android**  
> *Tích hợp khép kín từ Động cơ Sáng tác Văn bản AI Thông minh đến Phòng thu Sách nói Neural TTS 48kHz chạy Offline trên thiết bị.*

---

## 🌟 ĐIỂM NỔI BẬT (HIGHLIGHTS)

- 🚀 **AI Novel Generation Engine (Lõi Viết Văn Tự Động):**
  - Cơ chế **Sliding-Window Context** thông minh: Kết hợp 3 chương gần nhất + tóm tắt cốt truyện toàn cục + hồ sơ nhân vật & thế giới quan (World Lore).
  - **Live Token Streaming Console:** Chữ chạy thời gian thực với vận tốc token/giây (TPS), đồng hồ tiến độ và hiệu ứng Cyberpunk Terminal mượt mà.
  - **6 Mẫu Cốt Truyện Chuẩn:** Web Novel (Tiểu thuyết mạng), Văn học cổ điển, Hard Sci-Fi, Ngôn tình / Lãng mạn, Trinh thám / Ly kỳ và Tùy chỉnh.

- 🔄 **Smart Multi-Key & Multi-Model Rotation Subsystem:**
  - Thuật toán **Model-First, Key-Second**: Luân chuyển thông minh các model Google Gemini (`gemini-2.5-flash`, `gemini-2.0-flash`, `gemini-1.5-flash`, `gemini-1.5-pro`).
  - Tự động nhận diện và phân loại lỗi HTTP 429: **RPM Cooldown** ngắn (30s) và **RPD Daily Exhaustion Lock** (30 giờ).
  - Tự động chuyển đổi dự phòng tức thì khi gặp lỗi 503 Overload, Timeout hoặc Stream trả về 0 token.

- 🎙️ **VieNeu-TTS Neural Audiobook Studio (Phòng Thu Sách Nói Offline):**
  - Chạy **Offline 100% On-Device** với **ONNX Runtime 1.20+ (XNNPACK FP32)** kết hợp bộ tách âm ngữ âm tự nhiên **Sea-G2P (Rust Native JNI)**.
  - Tích hợp thẻ cảm xúc nhân vật sống động: `[cười]`, `[thở dài]`, `[hắng giọng]`,...
  - Hỗ trợ phát âm chuẩn danh từ và thuật ngữ tiếng Anh xen kẽ qua thẻ `<en>...</en>`.
  - Bộ phân đoạn câu văn thông minh (`SmartTextSegmenter`) tránh nghẽn bộ nhớ khi đọc chương dài.
  - Xuất file âm thanh chất lượng phòng thu **Lossless 48kHz 16-bit Mono RIFF WAV** vào thư mục `Music/AINovelist`.

- 🎧 **Dual Audio Player & 40-Bar Real-Time Waveform:**
  - Bộ trực quan hóa sóng âm 40 cột chuyển động mượt mà theo biên độ tần số âm thanh.
  - Thanh tua thời gian chính xác (Precision Scrubber) 50ms polling tối ưu pin.
  - Trình phát thử âm thanh giọng đọc tức thời (Instant Voice Sample Previewer).

- ⚡ **Chạy Ngầm Bền Bỉ (Background Persistence):**
  - **Android Foreground Service + Partial WakeLock**: Đảm bảo cả hai tiến trình viết truyện và tổng hợp âm thanh tiếp tục chạy xuyên suốt khi tắt màn hình hoặc chuyển ứng dụng.
  - **Live Notifications**: Điều khiển Tạm dừng (Pause), Tiếp tục (Resume), Hủy (Cancel) ngay trên thanh thông báo.

- 🎨 **Kiến Trúc Giao Diện Zero Text Clipping & Song Ngữ (VI - EN):**
  - Jetpack Compose + Material 3 (Material You Dynamic Colors, OLED Dark Theme).
  - Thiết kế thích ứng an toàn, hỗ trợ tỷ lệ phóng to chữ (Font Scale) từ **1.0x đến 2.0x** mà không bao giờ bị cắt chữ.
  - Chuyển đổi ngôn ngữ Việt - Anh linh hoạt toàn diện.

---

## 🏗️ KIẾN TRÚC HỆ THỐNG (SYSTEM ARCHITECTURE)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        UI LAYER (Jetpack Compose)                      │
│  - Material 3 / Dynamic Colors / Neon Accents / OLED Dark Background   │
│  - Screens: Dashboard, OutlineEditor, LiveConsole, Reader, StudioTTS   │
│  - Components: WaveformVisualizer, AudioPlayerCard, VoicePickerSheet   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ StateFlow / Coroutines
┌───────────────────────────────────▼────────────────────────────────────┐
│                       VIEWMODEL & DOMAIN LAYER                         │
│  - DashboardViewModel, GeneratorViewModel, SettingsViewModel           │
│  - AudioStudioViewModel, VoicePickerViewModel, ReaderViewModel         │
│  - AppViewModelProvider & AppContainer (Lightweight Dependency Inject) │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
┌───────────────────▼──────────────┐   ┌─────────────▼───────────────────┐
│       NOVEL WRITING ENGINE       │   │      VIENEU-TTS AUDIO ENGINE    │
│  - SlidingWindowContextManager   │   │  - VieNeuOnnxEngine (ONNX FP32) │
│  - RotationManager (Gemini Keys) │   │  - SeaG2P (Rust Native JNI)     │
│  - OkHttpLlmClient (SSE Stream)  │   │  - SmartTextSegmenter           │
│  - PromptTemplateService         │   │  - WavWriter (48kHz Lossless)   │
└───────────────────┬──────────────┘   └─────────────┬───────────────────┘
                    │                                │
┌───────────────────▼────────────────────────────────▼───────────────────┐
│                      BACKGROUND & SYSTEM SERVICES                      │
│  - NovelForegroundService (Chạy ngầm khi tắt màn hình)                 │
│  - AudioPlayerManager (Dual Player Engine + ExoPlayer/AudioTrack)      │
│  - NotificationHelper (Live Interactive Notifications)                 │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                          STORAGE & SECURITY                            │
│  - Room Database: novels, chapters, outlines, audio_tracks, quota_logs │
│  - Android Keystore / EncryptedSharedPreferences (Bảo mật API Keys)    │
│  - Android MediaStore API: Lưu và xuất trọn gói Audio ZIP / WAV        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 📂 CẤU TRÚC THƯ MỤC NGUỒN

```
AI-Novel-Android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/vieneu/          # Mô hình ONNX, từ điển G2P & danh sách giọng
│   │   │   ├── jniLibs/                # Thư viện native libsea_g2p (arm64-v8a, x86_64)
│   │   │   ├── java/com/ainovel/audiobook/
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/          # Room DB, DAOs, Entities, Converters
│   │   │   │   │   ├── remote/         # OkHttp LLM Client, Prompt Templates
│   │   │   │   │   ├── repository/     # Novel, Quota, Voice Repositories
│   │   │   │   │   └── security/       # Android Keystore Hardware Encryption
│   │   │   │   ├── domain/
│   │   │   │   │   ├── engine/         # NovelGenerationEngine, RotationManager
│   │   │   │   │   ├── model/          # ModelConfig, NovelTemplate, StreamEvent
│   │   │   │   │   └── usecase/        # Speech Synthesis & Preview UseCases
│   │   │   │   ├── player/             # AudioPlayerManager, WaveformSampler
│   │   │   │   ├── service/            # NovelForegroundService, NotificationHelper
│   │   │   │   ├── tts/engine/         # VieNeuOnnxEngine, SeaG2P, WavWriter
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/     # Waveform, AudioCard, VoicePickerSheet
│   │   │   │   │   ├── navigation/     # Jetpack Navigation Graph
│   │   │   │   │   ├── screens/        # 6 Màn hình chính Compose UI
│   │   │   │   │   ├── theme/          # Color, Type, Material 3 Theme
│   │   │   │   │   └── viewmodel/      # ViewModels & Dependency Providers
│   │   │   │   ├── AINovelApplication.kt
│   │   │   │   └── MainActivity.kt
│   │   │   └── res/                    # Song ngữ values/ (EN) & values-vi/ (VI)
│   │   └── test/                       # 5 Comprehensive Verification Test Suites
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🛠️ YÊU CẦU HỆ THỐNG & CÀI ĐẶT

### Yêu cầu:
- **Android OS:** Android 7.0 (API Level 24) trở lên.
- **Khuyến nghị:** Android 10+ (API Level 29+), RAM 4GB trở lên (để chạy Neural TTS On-Device mượt mà).
- **Môi trường phát triển:** Android Studio Ladybug / Meerkat (hoặc mới hơn), JDK 21, Kotlin 2.0+.

### Biên dịch & Cài đặt qua dòng lệnh:
```bash
# 1. Clone mã nguồn
git clone https://github.com/skul9x/AINovel-Android.git
cd AINovel-Android

# 2. Chạy toàn bộ Unit Test Suite (30/30 test cases)
./gradlew testDebugUnitTest

# 3. Biên dịch file APK Debug
./gradlew assembleDebug

# 4. Cài đặt trực tiếp lên thiết bị Android kết nối qua ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 BỘ KIỂM THỬ TỔNG HỢP (TEST SUITE)

Dự án đi kèm bộ kiểm thử toàn diện 100% không phụ thuộc thiết bị ngoài (chạy bằng Robolectric & In-Memory Room Database):

| Test File | Nội dung kiểm thử | Trạng thái |
|:---|:---|:---:|
| `Phase1DatabaseTest.kt` | Khởi tạo Room DB, Seed Giọng nói, Khóa CASCADE, Mã hóa Keystore | ✅ PASS (5/5) |
| `Phase2AiRotationTest.kt` | Sliding-Window, SSE Streaming, Xoay vòng Gemini Key/Model, Phân loại 429 RPM/RPD | ✅ PASS (5/5) |
| `Phase3TtsEngineTest.kt` | VieNeu-TTS Engine, Sea-G2P JNI, Phân đoạn câu, Xuất 48kHz RIFF WAV | ✅ PASS (6/6) |
| `Phase4ServicePlayerTest.kt` | Foreground Service, Live Notification, Audio Player & Waveform 40-bar | ✅ PASS (6/6) |
| `Phase5ComposeUiTest.kt` | Giao diện Material 3, Font Scale 1.0x-2.0x Zero Clipping, Song ngữ Việt - Anh | ✅ PASS (8/8) |

---

## 🔒 BẢO MẬT & QUYỀN RIÊNG TƯ

1. **Bảo mật API Key:** Các khóa Google Gemini API Key được mã hóa phần cứng bằng **Android Keystore (AES-256 GCM)**. Ứng dụng không bao giờ lưu trữ hay truyền tải API Key dưới dạng plain text.
2. **Quyền riêng tư tuyệt đối:** Tính năng tổng hợp giọng nói VieNeu-TTS chạy hoàn toàn offline trên phần cứng của bạn, dữ liệu tiểu thuyết không bao giờ bị chia sẻ cho bên thứ ba ngoại trừ endpoint LLM do chính bạn thiết lập.

---

## 📜 GIẤY PHÉP (LICENSE)

Dự án được phân phối dưới giấy phép **MIT License**. Chi tiết xem tại tệp `LICENSE`.
