package com.ainovel.audiobook.tts.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.ainovel.audiobook.tts.engine.VieNeuConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Storage manager for audio preview caching, MediaStore export, and batch ZIP archiving.
 * Saves under `Music/AINovelist`.
 */
object AudioStorageManager {

    const val ALBUM_DIRECTORY = "Music/AINovelist"
    const val DEFAULT_PREFIX = "AINovelist"
    const val CACHE_DIR_NAME = "audio_preview"
    const val MIME_TYPE_WAV = "audio/wav"

    private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun generateFileName(
        speakerName: String,
        chapterTitle: String? = null,
        timestampMs: Long = System.currentTimeMillis(),
        extension: String = "wav"
    ): String {
        val sanitizedSpeaker = speakerName
            .replace("\\s+".toRegex(), "_")
            .replace("[^a-zA-Z0-9_\\-]".toRegex(), "")
            .ifEmpty { "Voice" }

        val prefix = if (!chapterTitle.isNullOrBlank()) {
            val sanitizedTitle = chapterTitle
                .replace("\\s+".toRegex(), "_")
                .replace("[^a-zA-Z0-9_\\-]".toRegex(), "")
                .take(30)
            "${DEFAULT_PREFIX}_${sanitizedTitle}_$sanitizedSpeaker"
        } else {
            "${DEFAULT_PREFIX}_$sanitizedSpeaker"
        }

        val timestamp = DATE_FORMAT.format(Date(timestampMs))
        val ext = extension.removePrefix(".")
        return "${prefix}_$timestamp.$ext"
    }

    fun getPreviewCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun saveToCache(
        context: Context,
        pcm16: ShortArray,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE,
        prefix: String = "preview"
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = getPreviewCacheDir(context)
        val file = File(cacheDir, "${prefix}_${System.currentTimeMillis()}.wav")
        WavWriter.writeWav(file, pcm16, sampleRate)
        file
    }

    suspend fun saveToCache(
        context: Context,
        wavBytes: ByteArray,
        prefix: String = "preview"
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = getPreviewCacheDir(context)
        val file = File(cacheDir, "${prefix}_${System.currentTimeMillis()}.wav")
        FileOutputStream(file).use { fos ->
            fos.write(wavBytes)
            fos.flush()
        }
        file
    }

    suspend fun exportToMediaStore(
        context: Context,
        pcm16: ShortArray,
        speakerName: String,
        sampleRate: Int = VieNeuConfig.SAMPLE_RATE,
        customFileName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val fileName = customFileName ?: generateFileName(speakerName)
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE_WAV)
            put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(".wav"))
            put(MediaStore.Audio.Media.ARTIST, speakerName)
            put(MediaStore.Audio.Media.ALBUM, DEFAULT_PREFIX)
            put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, ALBUM_DIRECTORY)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collectionUri, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(itemUri)?.use { outStream ->
                WavWriter.writeWav(outStream, pcm16, sampleRate)
            } ?: throw IllegalStateException("Could not open output stream for URI: $itemUri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            itemUri
        } catch (e: Exception) {
            try {
                resolver.delete(itemUri, null, null)
            } catch (_: Exception) {}
            throw e
        }
    }

    suspend fun exportToMediaStore(
        context: Context,
        wavFile: File,
        speakerName: String,
        customFileName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val fileName = customFileName ?: generateFileName(speakerName)
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE_WAV)
            put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(".wav"))
            put(MediaStore.Audio.Media.ARTIST, speakerName)
            put(MediaStore.Audio.Media.ALBUM, DEFAULT_PREFIX)
            put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, ALBUM_DIRECTORY)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collectionUri, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(itemUri)?.use { outStream ->
                FileInputStream(wavFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            } ?: throw IllegalStateException("Could not open output stream for URI: $itemUri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            itemUri
        } catch (e: Exception) {
            try {
                resolver.delete(itemUri, null, null)
            } catch (_: Exception) {}
            throw e
        }
    }

    /**
     * Packages a collection of chapter WAV files into a single ZIP archive.
     */
    fun createZipArchive(files: List<File>, destinationZip: File): File {
        destinationZip.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(destinationZip)).use { zos ->
            val buffer = ByteArray(8192)
            for (file in files) {
                if (!file.exists()) continue
                val entry = ZipEntry(file.name)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
                zos.closeEntry()
            }
        }
        return destinationZip
    }

    fun clearCache(context: Context): Int {
        val cacheDir = getPreviewCacheDir(context)
        val files = cacheDir.listFiles() ?: return 0
        var deleted = 0
        for (f in files) {
            if (f.isFile && f.delete()) {
                deleted++
            }
        }
        return deleted
    }
}
