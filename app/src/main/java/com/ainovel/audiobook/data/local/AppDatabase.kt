package com.ainovel.audiobook.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ainovel.audiobook.data.local.converter.Converters
import com.ainovel.audiobook.data.local.dao.*
import com.ainovel.audiobook.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        OutlineEntity::class,
        AudioTrackEntity::class,
        VoicePresetEntity::class,
        ApiKeyEntity::class,
        QuotaLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun outlineDao(): OutlineDao
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun voicePresetDao(): VoicePresetDao
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun quotaLogDao(): QuotaLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ai_novelist.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).seedDefaultVoicePresets()
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemoryDatabase(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }

    suspend fun seedDefaultVoicePresets() {
        val defaultPresets = listOf(
            VoicePresetEntity(
                id = "hanoi_nu_kechuyen",
                name = "Hà Nội - Nữ Kể Chuyện",
                description = "Giọng Bắc Bộ truyền cảm, thanh thoát, phù hợp tiểu thuyết văn học và truyện dài.",
                gender = "Nữ",
                region = "Bắc",
                style = "ke_chuyen",
                sampleAudioUri = "samples/hanoi_nu_kechuyen.wav",
                isFavorite = true
            ),
            VoicePresetEntity(
                id = "saigon_nam_truyencam",
                name = "Sài Gòn - Nam Truyền Cảm",
                description = "Giọng Nam Bộ ấm áp, lôi cuốn, thích hợp thể loại trinh thám và phiêu lưu.",
                gender = "Nam",
                region = "Nam",
                style = "ke_chuyen",
                sampleAudioUri = "samples/saigon_nam_truyencam.wav",
                isFavorite = true
            ),
            VoicePresetEntity(
                id = "danang_nu_diudang",
                name = "Đà Nẵng - Nữ Dịu Dàng",
                description = "Giọng Trung Bộ dịu dàng, sâu lắng, phù hợp với truyện tình cảm ngôn tình.",
                gender = "Nữ",
                region = "Trung",
                style = "tu_nhien",
                sampleAudioUri = "samples/danang_nu_diudang.wav",
                isFavorite = false
            ),
            VoicePresetEntity(
                id = "hanoi_nam_tintuc",
                name = "Hà Nội - Nam Phát Thanh",
                description = "Giọng đọc chuẩn mực, dõng dạc, phù hợp giáo trình và tài liệu học thuật.",
                gender = "Nam",
                region = "Bắc",
                style = "tin_tuc",
                sampleAudioUri = "samples/hanoi_nam_tintuc.wav",
                isFavorite = false
            ),
            VoicePresetEntity(
                id = "saigon_nu_hiendai",
                name = "Sài Gòn - Nữ Hiện Đại",
                description = "Giọng nữ Nam Bộ trẻ trung, năng động, thích hợp thể loại kỳ ảo và khoa học viễn tưởng.",
                gender = "Nữ",
                region = "Nam",
                style = "tu_nhien",
                sampleAudioUri = "samples/saigon_nu_hiendai.wav",
                isFavorite = false
            ),
            VoicePresetEntity(
                id = "hanoi_nu_tramam",
                name = "Hà Nội - Nữ Trầm Ấm",
                description = "Giọng nữ trầm tĩnh, nội tâm, phù hợp cho hồi ký và tự sự.",
                gender = "Nữ",
                region = "Bắc",
                style = "tu_nhien",
                sampleAudioUri = "samples/hanoi_nu_tramam.wav",
                isFavorite = false
            ),
            VoicePresetEntity(
                id = "hue_nu_cungdinh",
                name = "Huế - Nữ Nhẹ Nhàng",
                description = "Giọng Huế ngọt ngào, hoài niệm, thích hợp truyện lịch sử dã sử.",
                gender = "Nữ",
                region = "Trung",
                style = "ke_chuyen",
                sampleAudioUri = "samples/hue_nu_cungdinh.wav",
                isFavorite = false
            ),
            VoicePresetEntity(
                id = "english_storyteller",
                name = "English - Storyteller",
                description = "Deep, articulated English voice suitable for Sci-Fi, fantasy, and global courseware.",
                gender = "Nam",
                region = "Quốc tế",
                style = "ke_chuyen",
                sampleAudioUri = "samples/english_storyteller.wav",
                isFavorite = true
            )
        )
        voicePresetDao().insertPresets(defaultPresets)
    }
}
