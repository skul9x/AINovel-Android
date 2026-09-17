package com.ainovel.audiobook

import android.app.Application
import android.content.Context
import com.ainovel.audiobook.data.local.AppDatabase
import com.ainovel.audiobook.data.local.dao.ApiKeyDao
import com.ainovel.audiobook.data.local.dao.QuotaLogDao
import com.ainovel.audiobook.data.remote.OkHttpLlmClient
import com.ainovel.audiobook.data.repository.NovelRepository
import com.ainovel.audiobook.data.repository.NovelRepositoryImpl
import com.ainovel.audiobook.data.repository.QuotaRepository
import com.ainovel.audiobook.data.repository.QuotaRepositoryImpl
import com.ainovel.audiobook.data.repository.VoiceRepository
import com.ainovel.audiobook.data.repository.VoiceRepositoryImpl
import com.ainovel.audiobook.data.security.SecureKeyStorage
import com.ainovel.audiobook.data.security.SecureKeyStorageImpl
import com.ainovel.audiobook.domain.engine.NovelGenerationEngine
import com.ainovel.audiobook.domain.engine.RotationManager
import com.ainovel.audiobook.player.AudioPlayerManager
import com.ainovel.audiobook.player.VoiceSamplePlayer

interface AppContainer {
    val database: AppDatabase
    val novelRepository: NovelRepository
    val quotaRepository: QuotaRepository
    val secureKeyStorage: SecureKeyStorage
    val apiKeyDao: ApiKeyDao
    val quotaLogDao: QuotaLogDao
    val rotationManager: RotationManager
    val llmClient: OkHttpLlmClient
    val novelGenerationEngine: NovelGenerationEngine
    val voiceRepository: VoiceRepository
    val audioPlayerManager: AudioPlayerManager
    val voiceSamplePlayer: VoiceSamplePlayer
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    override val apiKeyDao: ApiKeyDao by lazy {
        database.apiKeyDao()
    }

    override val quotaLogDao: QuotaLogDao by lazy {
        database.quotaLogDao()
    }

    override val novelRepository: NovelRepository by lazy {
        NovelRepositoryImpl(
            novelDao = database.novelDao(),
            chapterDao = database.chapterDao(),
            outlineDao = database.outlineDao()
        )
    }

    override val quotaRepository: QuotaRepository by lazy {
        QuotaRepositoryImpl(quotaLogDao)
    }

    override val secureKeyStorage: SecureKeyStorage by lazy {
        SecureKeyStorageImpl(context, apiKeyDao)
    }

    override val rotationManager: RotationManager by lazy {
        RotationManager(
            apiKeyDao = apiKeyDao,
            secureKeyStorage = secureKeyStorage,
            quotaRepository = quotaRepository
        )
    }

    override val llmClient: OkHttpLlmClient by lazy {
        OkHttpLlmClient()
    }

    override val novelGenerationEngine: NovelGenerationEngine by lazy {
        NovelGenerationEngine(
            rotationManager = rotationManager,
            llmClient = llmClient,
            novelRepository = novelRepository
        )
    }

    override val voiceRepository: VoiceRepository by lazy {
        VoiceRepositoryImpl(database.voicePresetDao())
    }

    override val audioPlayerManager: AudioPlayerManager by lazy {
        AudioPlayerManager()
    }

    override val voiceSamplePlayer: VoiceSamplePlayer by lazy {
        VoiceSamplePlayer()
    }
}

class AINovelApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
