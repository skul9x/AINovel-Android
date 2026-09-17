# 📋 HANDOVER DOCUMENT: AI Novelist & Audiobook Mobile

**Thời gian lưu:** 17/09/2026 - 16:00 (GMT+7)  
**Dự án:** `AI-Novel-Android/` (thuộc hệ sinh thái `AI_Novel-main`)  
**Nền tảng:** Android 7.0+ (Kotlin 2.0 + Jetpack Compose + Material 3)  
**Trạng thái hiện tại:** ✅ **Đã hoàn thành 5/5 Phase + Nối dây AI thật + Đã cài đặt lên điện thoại**

---

## 🚀 TỔNG KẾT TIẾN ĐỘ

### 1. Các Phase đã hoàn thành (100%):
- [x] **Phase 01:** Cấu trúc dự án, Room Database (`AppDatabase`), Entities, DAOs, Mã hóa Key Android Keystore.
- [x] **Phase 02:** Động cơ AI Novel Writing Engine (`NovelGenerationEngine`), Sliding-Window Context, Xoay vòng đa key/đa model Gemini (`RotationManager`), xử lý lỗi 429 RPM/RPD.
- [x] **Phase 03:** VieNeu-TTS v3 Turbo 48kHz Neural Audio Engine (ONNX Runtime FP32 + Sea-G2P Rust JNI) chạy Offline trên máy, Voice Picker Studio, trích xuất WAV lossless.
- [x] **Phase 04:** Android Foreground Service (`NovelForegroundService`), Live Notification tiến trình viết và thu âm khi tắt màn hình, Dual Audio Player, 40-bar Waveform Visualizer.
- [x] **Phase 05:** Giao diện Mockup-Driven Compose UI, tuân thủ Zero Text Clipping (hỗ trợ phóng to font tới 2.0x), song ngữ Việt - Anh hoàn chỉnh.
- [x] **Nối dây Động cơ & Production Fix:** Tạo `AINovelApplication`, `AppViewModelProvider` tiêm các engine thật vào ViewModels, loại bỏ toàn bộ mock demo data, xuất file APK và cài đặt thành công lên điện thoại thật (`3B658D010BU00000`).

---

## 🔧 QUYẾT ĐỊNH QUAN TRỌNG ĐÃ CHỐT:
1. **Kiến trúc DI:** Dùng `AppContainer` và `AppViewModelProvider.createFactory` (thay vì phụ thuộc framework nặng như Hilt/Dagger), giúp build cực nhanh và độc lập.
2. **Bảo mật API Key:** Mã hóa phần cứng qua Android Keystore (`SecureKeyStorageImpl`), key không bao giờ lưu text trần.
3. **Sạch sẽ dữ liệu:** Khi ứng dụng khởi chạy ở môi trường thật, toàn bộ dữ liệu mẫu (demo Chapter 4) đã được loại bỏ; app hiển thị đúng trạng thái 0 tác phẩm trong database.
4. **Tương thích kiểm thử:** Toàn bộ 5 file test (`Phase1DatabaseTest.kt` đến `Phase5ComposeUiTest.kt`) đều chạy thành công 100% không bị ảnh hưởng.

---

## 📁 FILES QUAN TRỌNG CẦN LƯU Ý:
- **Application & Factory:**
  - [`AINovelApplication.kt`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/src/main/java/com/ainovel/audiobook/AINovelApplication.kt)
  - [`AppViewModelProvider.kt`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/src/main/java/com/ainovel/audiobook/ui/viewmodel/AppViewModelProvider.kt)
- **Engine & Core:**
  - [`NovelGenerationEngine.kt`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/src/main/java/com/ainovel/audiobook/domain/engine/NovelGenerationEngine.kt)
  - [`RotationManager.kt`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/src/main/java/com/ainovel/audiobook/domain/engine/RotationManager.kt)
  - [`OkHttpLlmClient.kt`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/src/main/java/com/ainovel/audiobook/data/remote/OkHttpLlmClient.kt)
- **UI Screens:**
  - [`StudioDashboardScreen.kt`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/src/main/java/com/ainovel/audiobook/ui/screens/StudioDashboardScreen.kt)
  - [`OutlineEditorScreen.kt`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/src/main/java/com/ainovel/audiobook/ui/screens/OutlineEditorScreen.kt)
  - [`LiveConsoleScreen.kt`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/src/main/java/com/ainovel/audiobook/ui/screens/LiveConsoleScreen.kt)
  - [`ReaderScreen.kt`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/src/main/java/com/ainovel/audiobook/ui/screens/ReaderScreen.kt)
  - [`AudioStudioScreen.kt`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/src/main/java/com/ainovel/audiobook/ui/screens/AudioStudioScreen.kt)
  - [`RotationSettingsScreen.kt`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/src/main/java/com/ainovel/audiobook/ui/screens/RotationSettingsScreen.kt)
- **APK Đã Build:**
  - [`app-debug.apk`](file:///home/skul9x/Desktop/Code/AI-Novel-Android/app/build/outputs/apk/debug/app-debug.apk)

---

## 🎯 GỢI Ý BƯỚC TIẾP THEO CHO SESSION SAU:
- Bổ sung màn hình **Worldview & Mind Library Editor** (quản lý chi tiết hồ sơ nhân vật & thế giới quan như bản Web).
- Thêm tính năng **Xuất trọn gói ZIP / DOCX / Markdown / EPUB** cho nội dung tiểu thuyết trên Android.
- Vẽ biểu đồ **Token Stats đồ họa** (Compose Canvas) theo dõi chi phí API.
