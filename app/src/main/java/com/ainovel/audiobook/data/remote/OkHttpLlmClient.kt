package com.ainovel.audiobook.data.remote

import android.util.Log
import com.ainovel.audiobook.domain.model.RotationCandidate
import com.ainovel.audiobook.domain.model.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OkHttpLlmClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    var defaultBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Executes streaming chat completion and emits tokens as a Kotlin Flow.
     */
    fun chatStream(
        candidate: RotationCandidate,
        systemPrompt: String,
        userPrompt: String,
        endpointUrl: String = defaultBaseUrl
    ): Flow<StreamEvent> = flow {
        val startTime = System.currentTimeMillis()
        val payload = buildOpenAiPayload(candidate.modelId, systemPrompt, userPrompt, stream = true)

        Log.d("AINovel_LLM", "chatStream calling: model=${candidate.modelId}, endpoint=$endpointUrl")

        val request = Request.Builder()
            .url(endpointUrl)
            .addHeader("Authorization", "Bearer ${candidate.rawApiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        var tokenCount = 0
        val fullTextBuilder = StringBuilder()

        try {
            val response = client.newCall(request).execute()
            val code = response.code
            Log.d("AINovel_LLM", "chatStream response code: $code")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e("AINovel_LLM", "chatStream error: HTTP $code - $errorBody")
                val isRetryable = (code == 429 || code == 503 || code >= 500)
                emit(StreamEvent.Error(code, errorBody, isRetryable))
                return@flow
            }

            val responseBody = response.body ?: run {
                Log.e("AINovel_LLM", "chatStream response body is empty")
                emit(StreamEvent.Error(500, "Response body is empty", true))
                return@flow
            }

            val reader = BufferedReader(InputStreamReader(responseBody.byteStream(), Charsets.UTF_8))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty() || currentLine.startsWith(":")) continue // Comment or keepalive

                if (currentLine.startsWith("data:")) {
                    val data = currentLine.removePrefix("data:").trim()
                    if (data == "[DONE]") break

                    try {
                        val chunkJson = JSONObject(data)
                        val choices = chunkJson.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                tokenCount++
                                fullTextBuilder.append(content)
                                emit(StreamEvent.Token(content, candidate.modelId))
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore chunk JSON parse errors
                    }
                }
            }

            val durationMs = System.currentTimeMillis() - startTime
            Log.d("AINovel_LLM", "chatStream completed: $tokenCount tokens in ${durationMs}ms")
            emit(StreamEvent.Completed(fullTextBuilder.toString(), tokenCount, durationMs))

        } catch (e: Exception) {
            Log.e("AINovel_LLM", "chatStream exception: ${e.message}", e)
            emit(StreamEvent.Error(500, e.localizedMessage ?: "Network error", true))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Non-streaming chat completion. Offloads execution to Dispatchers.IO.
     */
    suspend fun chat(
        candidate: RotationCandidate,
        systemPrompt: String,
        userPrompt: String,
        endpointUrl: String = defaultBaseUrl
    ): Result<String> = withContext(Dispatchers.IO) {
        val payload = buildOpenAiPayload(candidate.modelId, systemPrompt, userPrompt, stream = false)

        Log.d("AINovel_LLM", "chat calling: model=${candidate.modelId}, endpoint=$endpointUrl")

        val request = Request.Builder()
            .url(endpointUrl)
            .addHeader("Authorization", "Bearer ${candidate.rawApiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            val code = response.code
            Log.d("AINovel_LLM", "chat response code: $code")

            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP error $code"
                Log.e("AINovel_LLM", "chat error: HTTP $code - $err")
                Result.failure(Exception("HTTP $code: $err"))
            } else {
                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val message = choices.getJSONObject(0).optJSONObject("message")
                    val content = message?.optString("content", "") ?: ""
                    Log.d("AINovel_LLM", "chat success: received ${content.length} chars")
                    Result.success(content)
                } else {
                    Log.e("AINovel_LLM", "chat returned no choices: $bodyString")
                    Result.failure(Exception("No choices returned from LLM"))
                }
            }
        } catch (e: Exception) {
            Log.e("AINovel_LLM", "chat exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildOpenAiPayload(
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        stream: Boolean
    ): JSONObject {
        val payload = JSONObject()
        payload.put("model", modelId)
        payload.put("stream", stream)

        val messages = JSONArray()

        if (systemPrompt.isNotBlank()) {
            val sysMsg = JSONObject()
            sysMsg.put("role", "system")
            sysMsg.put("content", systemPrompt)
            messages.put(sysMsg)
        }

        val userMsg = JSONObject()
        userMsg.put("role", "user")
        userMsg.put("content", userPrompt)
        messages.put(userMsg)

        payload.put("messages", messages)
        return payload
    }
}
