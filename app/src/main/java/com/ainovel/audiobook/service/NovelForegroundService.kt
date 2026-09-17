package com.ainovel.audiobook.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Operating mode of [NovelForegroundService].
 */
enum class ServiceMode {
    IDLE,
    TEXT_GENERATION,
    AUDIO_SYNTHESIS,
    MEDIA_PLAYBACK
}

/**
 * Observable execution state of the foreground service.
 */
data class ServiceState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val mode: ServiceMode = ServiceMode.IDLE,
    val currentChapter: Int = 0,
    val totalChapters: Int = 0,
    val progressPercent: Int = 0,
    val tokensPerSec: Float = 0f,
    val currentSegment: Int = 0,
    val totalSegments: Int = 0,
    val rtf: Float = 0f,
    val title: String = "",
    val chapterTitle: String = ""
)

/**
 * Resilient Android Foreground Service with partial WakeLock to keep novel generation
 * and neural TTS synthesis running uninterrupted when the screen turns off.
 *
 * Compliant with Android 14+ (API 34+) dataSync and mediaPlayback service types.
 */
class NovelForegroundService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationManager: NotificationManager? = null

    inner class LocalBinder : Binder() {
        fun getService(): NovelForegroundService = this@NovelForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        NotificationHelper.createNotificationChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            NotificationHelper.ACTION_START_TEXT -> {
                val current = intent.getIntExtra(NotificationHelper.EXTRA_CURRENT_CHAPTER, 1)
                val total = intent.getIntExtra(NotificationHelper.EXTRA_TOTAL_CHAPTERS, 1)
                val percent = intent.getIntExtra(NotificationHelper.EXTRA_PROGRESS_PERCENT, 0)
                val speed = intent.getFloatExtra(NotificationHelper.EXTRA_TOKENS_PER_SEC, 0f)
                startTextGenerationForeground(current, total, percent, speed)
            }

            NotificationHelper.ACTION_START_AUDIO -> {
                val chapter = intent.getIntExtra(NotificationHelper.EXTRA_CURRENT_CHAPTER, 1)
                val curSeg = intent.getIntExtra(NotificationHelper.EXTRA_CURRENT_SEGMENT, 1)
                val totalSegs = intent.getIntExtra(NotificationHelper.EXTRA_TOTAL_SEGMENTS, 1)
                val rtf = intent.getFloatExtra(NotificationHelper.EXTRA_RTF, 1.0f)
                startAudioSynthesisForeground(chapter, curSeg, totalSegs, rtf)
            }

            NotificationHelper.ACTION_PAUSE -> {
                pauseService()
            }

            NotificationHelper.ACTION_RESUME -> {
                resumeService()
            }

            NotificationHelper.ACTION_CANCEL, NotificationHelper.ACTION_STOP -> {
                cancelService()
            }
        }

        return START_STICKY
    }

    fun acquireWakeLock(timeoutMs: Long = 30 * 60 * 1000L) {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "com.ainovel.audiobook:NovelGenerationWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(timeoutMs)
        }
    }

    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {}
    }

    fun isWakeLockHeld(): Boolean = wakeLock?.isHeld ?: false

    private fun startTextGenerationForeground(
        currentChapter: Int,
        totalChapters: Int,
        percent: Int,
        tokensPerSec: Float
    ) {
        acquireWakeLock()
        val notif = NotificationHelper.buildTextGenerationNotification(
            this,
            currentChapter,
            totalChapters,
            percent,
            tokensPerSec,
            isPaused = false
        )

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID_GENERATION,
            notif,
            serviceType
        )

        _serviceState.value = ServiceState(
            isRunning = true,
            isPaused = false,
            mode = ServiceMode.TEXT_GENERATION,
            currentChapter = currentChapter,
            totalChapters = totalChapters,
            progressPercent = percent,
            tokensPerSec = tokensPerSec
        )
    }

    private fun startAudioSynthesisForeground(
        chapterIndex: Int,
        currentSeg: Int,
        totalSegs: Int,
        rtf: Float
    ) {
        acquireWakeLock()
        val notif = NotificationHelper.buildAudioSynthesisNotification(
            this,
            chapterIndex,
            currentSeg,
            totalSegs,
            rtf,
            isPaused = false
        )

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID_GENERATION,
            notif,
            serviceType
        )

        _serviceState.value = ServiceState(
            isRunning = true,
            isPaused = false,
            mode = ServiceMode.AUDIO_SYNTHESIS,
            currentChapter = chapterIndex,
            currentSegment = currentSeg,
            totalSegments = totalSegs,
            rtf = rtf
        )
    }

    /**
     * Updates ongoing text generation progress and refreshes notification.
     */
    fun updateTextProgress(currentChapter: Int, totalChapters: Int, percent: Int, tokensPerSec: Float) {
        val current = _serviceState.value
        if (!current.isRunning || current.isPaused) return

        _serviceState.value = current.copy(
            currentChapter = currentChapter,
            totalChapters = totalChapters,
            progressPercent = percent,
            tokensPerSec = tokensPerSec
        )

        val notif = NotificationHelper.buildTextGenerationNotification(
            this,
            currentChapter,
            totalChapters,
            percent,
            tokensPerSec,
            isPaused = false
        )
        notificationManager?.notify(NotificationHelper.NOTIFICATION_ID_GENERATION, notif)
    }

    /**
     * Updates ongoing audio synthesis progress and refreshes notification.
     */
    fun updateAudioProgress(chapterIndex: Int, currentSeg: Int, totalSegs: Int, rtf: Float) {
        val current = _serviceState.value
        if (!current.isRunning || current.isPaused) return

        _serviceState.value = current.copy(
            currentChapter = chapterIndex,
            currentSegment = currentSeg,
            totalSegments = totalSegs,
            rtf = rtf
        )

        val notif = NotificationHelper.buildAudioSynthesisNotification(
            this,
            chapterIndex,
            currentSeg,
            totalSegs,
            rtf,
            isPaused = false
        )
        notificationManager?.notify(NotificationHelper.NOTIFICATION_ID_GENERATION, notif)
    }

    private fun pauseService() {
        val current = _serviceState.value
        if (!current.isRunning || current.isPaused) return

        _serviceState.value = current.copy(isPaused = true)
        releaseWakeLock()

        val notif = when (current.mode) {
            ServiceMode.TEXT_GENERATION -> NotificationHelper.buildTextGenerationNotification(
                this,
                current.currentChapter,
                current.totalChapters,
                current.progressPercent,
                current.tokensPerSec,
                isPaused = true
            )
            ServiceMode.AUDIO_SYNTHESIS -> NotificationHelper.buildAudioSynthesisNotification(
                this,
                current.currentChapter,
                current.currentSegment,
                current.totalSegments,
                current.rtf,
                isPaused = true
            )
            else -> return
        }
        notificationManager?.notify(NotificationHelper.NOTIFICATION_ID_GENERATION, notif)
    }

    private fun resumeService() {
        val current = _serviceState.value
        if (!current.isRunning || !current.isPaused) return

        acquireWakeLock()
        _serviceState.value = current.copy(isPaused = false)

        val notif = when (current.mode) {
            ServiceMode.TEXT_GENERATION -> NotificationHelper.buildTextGenerationNotification(
                this,
                current.currentChapter,
                current.totalChapters,
                current.progressPercent,
                current.tokensPerSec,
                isPaused = false
            )
            ServiceMode.AUDIO_SYNTHESIS -> NotificationHelper.buildAudioSynthesisNotification(
                this,
                current.currentChapter,
                current.currentSegment,
                current.totalSegments,
                current.rtf,
                isPaused = false
            )
            else -> return
        }
        notificationManager?.notify(NotificationHelper.NOTIFICATION_ID_GENERATION, notif)
    }

    private fun cancelService() {
        releaseWakeLock()
        _serviceState.value = ServiceState()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        _serviceState.value = ServiceState()
        super.onDestroy()
    }

    companion object {
        private val _serviceState = MutableStateFlow(ServiceState())
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

        fun startTextGeneration(
            context: Context,
            currentChapter: Int,
            totalChapters: Int,
            percent: Int = 0,
            tokensPerSec: Float = 0f
        ) {
            val intent = Intent(context, NovelForegroundService::class.java).apply {
                action = NotificationHelper.ACTION_START_TEXT
                putExtra(NotificationHelper.EXTRA_CURRENT_CHAPTER, currentChapter)
                putExtra(NotificationHelper.EXTRA_TOTAL_CHAPTERS, totalChapters)
                putExtra(NotificationHelper.EXTRA_PROGRESS_PERCENT, percent)
                putExtra(NotificationHelper.EXTRA_TOKENS_PER_SEC, tokensPerSec)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startAudioSynthesis(
            context: Context,
            chapterIndex: Int,
            currentSeg: Int = 0,
            totalSegs: Int = 1,
            rtf: Float = 0f
        ) {
            val intent = Intent(context, NovelForegroundService::class.java).apply {
                action = NotificationHelper.ACTION_START_AUDIO
                putExtra(NotificationHelper.EXTRA_CURRENT_CHAPTER, chapterIndex)
                putExtra(NotificationHelper.EXTRA_CURRENT_SEGMENT, currentSeg)
                putExtra(NotificationHelper.EXTRA_TOTAL_SEGMENTS, totalSegs)
                putExtra(NotificationHelper.EXTRA_RTF, rtf)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, NovelForegroundService::class.java).apply {
                action = NotificationHelper.ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, NovelForegroundService::class.java).apply {
                action = NotificationHelper.ACTION_RESUME
            }
            context.startService(intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, NovelForegroundService::class.java).apply {
                action = NotificationHelper.ACTION_CANCEL
            }
            context.startService(intent)
        }

        /**
         * Direct testing hook to reset state between test runs.
         */
        fun resetStateForTesting() {
            _serviceState.value = ServiceState()
        }
    }
}
