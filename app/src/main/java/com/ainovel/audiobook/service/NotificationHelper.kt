package com.ainovel.audiobook.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Builds NotificationCompat instances, notification channels, and pending action intents
 * for foreground text generation, neural TTS audio synthesis, and media playback.
 */
object NotificationHelper {

    const val CHANNEL_GENERATION_SERVICE = "channel_generation_service"
    const val CHANNEL_MEDIA_PLAYBACK = "channel_media_playback"

    const val NOTIFICATION_ID_GENERATION = 1001
    const val NOTIFICATION_ID_PLAYBACK = 1002

    const val ACTION_PAUSE = "com.ainovel.audiobook.action.PAUSE"
    const val ACTION_RESUME = "com.ainovel.audiobook.action.RESUME"
    const val ACTION_CANCEL = "com.ainovel.audiobook.action.CANCEL"
    const val ACTION_START_TEXT = "com.ainovel.audiobook.action.START_TEXT"
    const val ACTION_START_AUDIO = "com.ainovel.audiobook.action.START_AUDIO"
    const val ACTION_PLAY = "com.ainovel.audiobook.action.PLAY"
    const val ACTION_STOP = "com.ainovel.audiobook.action.STOP"

    const val EXTRA_CURRENT_CHAPTER = "extra_current_chapter"
    const val EXTRA_TOTAL_CHAPTERS = "extra_total_chapters"
    const val EXTRA_PROGRESS_PERCENT = "extra_progress_percent"
    const val EXTRA_TOKENS_PER_SEC = "extra_tokens_per_sec"
    const val EXTRA_CURRENT_SEGMENT = "extra_current_segment"
    const val EXTRA_TOTAL_SEGMENTS = "extra_total_segments"
    const val EXTRA_RTF = "extra_rtf"

    /**
     * Creates notification channels required on Android 8.0 (API 26) and above.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val genChannel = NotificationChannel(
                CHANNEL_GENERATION_SERVICE,
                "AI Novel & Audiobook Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for novel writing and neural speech synthesis"
                setShowBadge(false)
            }

            val playChannel = NotificationChannel(
                CHANNEL_MEDIA_PLAYBACK,
                "Audiobook Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active audiobook playback controls"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(genChannel)
            notificationManager.createNotificationChannel(playChannel)
        }
    }

    /**
     * Constructs a PendingIntent sending an action intent to [NovelForegroundService].
     */
    fun createActionPendingIntent(context: Context, action: String, requestCode: Int = 0): PendingIntent {
        val intent = Intent(context, NovelForegroundService::class.java).apply {
            this.action = action
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(context, requestCode, intent, flags)
    }

    /**
     * Builds notification for batch chapter text generation.
     * Content: `Writing Chapter 3/15 (45%) • 48 tokens/s [Pause] [Cancel]`
     */
    fun buildTextGenerationNotification(
        context: Context,
        currentChapter: Int,
        totalChapters: Int,
        percent: Int,
        tokensPerSec: Float,
        isPaused: Boolean
    ): Notification {
        createNotificationChannels(context)

        val speedText = String.format(Locale.US, "%.0f", tokensPerSec)
        val contentText = if (isPaused) {
            "Paused • Chapter $currentChapter/$totalChapters ($percent%) • $speedText tokens/s"
        } else {
            "Writing Chapter $currentChapter/$totalChapters ($percent%) • $speedText tokens/s"
        }

        val toggleAction = if (isPaused) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Resume",
                createActionPendingIntent(context, ACTION_RESUME, 101)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                createActionPendingIntent(context, ACTION_PAUSE, 102)
            ).build()
        }

        val cancelAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel",
            createActionPendingIntent(context, ACTION_CANCEL, 103)
        ).build()

        return NotificationCompat.Builder(context, CHANNEL_GENERATION_SERVICE)
            .setContentTitle("AI Novelist - Text Generation")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(toggleAction)
            .addAction(cancelAction)
            .build()
    }

    /**
     * Builds notification for batch neural audio synthesis.
     * Content: `Synthesizing Audio: Chapter 3 (Seg 12/28) • RTF 0.85x [Pause] [Cancel]`
     */
    fun buildAudioSynthesisNotification(
        context: Context,
        chapterIndex: Int,
        currentSeg: Int,
        totalSegs: Int,
        rtf: Float,
        isPaused: Boolean
    ): Notification {
        createNotificationChannels(context)

        val rtfText = String.format(Locale.US, "%.2f", rtf)
        val contentText = if (isPaused) {
            "Paused • Chapter $chapterIndex (Seg $currentSeg/$totalSegs) • RTF ${rtfText}x"
        } else {
            "Synthesizing Audio: Chapter $chapterIndex (Seg $currentSeg/$totalSegs) • RTF ${rtfText}x"
        }

        val toggleAction = if (isPaused) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Resume",
                createActionPendingIntent(context, ACTION_RESUME, 201)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                createActionPendingIntent(context, ACTION_PAUSE, 202)
            ).build()
        }

        val cancelAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel",
            createActionPendingIntent(context, ACTION_CANCEL, 203)
        ).build()

        val maxProgress = totalSegs.coerceAtLeast(1)
        val currentProgress = currentSeg.coerceIn(0, maxProgress)

        return NotificationCompat.Builder(context, CHANNEL_GENERATION_SERVICE)
            .setContentTitle("AI Novelist - Audio Synthesis")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .setProgress(maxProgress, currentProgress, false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(toggleAction)
            .addAction(cancelAction)
            .build()
    }

    /**
     * Builds lockscreen media playback notification.
     */
    fun buildMediaPlaybackNotification(
        context: Context,
        title: String,
        chapterTitle: String,
        isPlaying: Boolean
    ): Notification {
        createNotificationChannels(context)

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                createActionPendingIntent(context, ACTION_PAUSE, 301)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                createActionPendingIntent(context, ACTION_RESUME, 302)
            ).build()
        }

        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            createActionPendingIntent(context, ACTION_STOP, 303)
        ).build()

        return NotificationCompat.Builder(context, CHANNEL_MEDIA_PLAYBACK)
            .setContentTitle(title)
            .setContentText(chapterTitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .build()
    }
}
