package com.ainovel.audiobook.tts.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Remote VieNeu-TTS server API client.
 * Connects to OpenAI-compatible `/v1/audio/speech` endpoint on self-hosted Docker servers.
 */
class RemoteVieNeuApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build(),
    var baseUrl: String = "http://10.0.2.2:8000"
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Synthesize speech using remote VieNeu-TTS API and return complete WAV audio bytes.
     */
    suspend fun synthesizeSpeech(
        text: String,
        voice: String = "Ngọc Huyền",
        model: String = "vieneu-tts-v3-turbo",
        responseFormat: String = "wav",
        endpointUrl: String = "$baseUrl/v1/audio/speech"
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("model", model)
                put("input", text)
                put("voice", voice)
                put("response_format", responseFormat)
            }

            val request = Request.Builder()
                .url(endpointUrl)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = response.body?.string() ?: "HTTP ${response.code}"
                return@withContext Result.failure(IllegalStateException("Remote TTS error ${response.code}: $errorMsg"))
            }

            val bytes = response.body?.bytes()
                ?: return@withContext Result.failure(IllegalStateException("Empty response body from remote TTS"))

            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Synthesize speech with chunked stream decoding, emitting raw audio byte chunks as they arrive.
     */
    fun synthesizeSpeechStream(
        text: String,
        voice: String = "Ngọc Huyền",
        model: String = "vieneu-tts-v3-turbo",
        endpointUrl: String = "$baseUrl/v1/audio/speech"
    ): Flow<ByteArray> = flow {
        val payload = JSONObject().apply {
            put("model", model)
            put("input", text)
            put("voice", voice)
            put("response_format", "wav")
            put("stream", true)
        }

        val request = Request.Builder()
            .url(endpointUrl)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorMsg = response.body?.string() ?: "HTTP ${response.code}"
            throw IllegalStateException("Remote TTS stream error ${response.code}: $errorMsg")
        }

        val responseBody = response.body ?: throw IllegalStateException("Empty response body")
        val inputStream: InputStream = responseBody.byteStream()
        val buffer = ByteArray(4096)
        var bytesRead: Int

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (bytesRead > 0) {
                    emit(buffer.copyOf(bytesRead))
                }
            }
        } finally {
            inputStream.close()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Decodes and concatenates chunked incoming stream bytes into a complete WAV ByteArray.
     */
    suspend fun collectStreamToWav(streamFlow: Flow<ByteArray>): ByteArray = withContext(Dispatchers.IO) {
        val baos = ByteArrayOutputStream()
        streamFlow.collect { chunk ->
            baos.write(chunk)
        }
        baos.toByteArray()
    }
}
