package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiProviderType
import com.example.data.model.CustomProviderConfig
import com.example.security.KeystoreCryptoManager
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiReplyResult(
    val text: String,
    val latencyMs: Long,
    val modelUsed: String
)

class LumiqAiEngine {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(50, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val fastLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = content {
                text(SYSTEM_PROMPT_FAST)
            }
        )
    }

    private val defaultFlashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(SYSTEM_PROMPT_MAIN)
            }
        )
    }

    private val argonReasoningModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            systemInstruction = content {
                text(SYSTEM_PROMPT_MAIN)
            }
        )
    }

    fun streamReply(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        memoryContext: String = "",
        activeProviderId: String,
        customProviders: List<CustomProviderConfig> = emptyList(),
        imageBitmap: Bitmap? = null
    ): Flow<String> = flow {
        val enrichedPrompt = buildString {
            if (memoryContext.isNotBlank()) {
                append("[User-Approved Memory Context]\n")
                append(memoryContext)
                append("\n\n")
            }
            if (history.isNotEmpty()) {
                append("[Recent Conversation]\n")
                history.takeLast(6).forEach { (role, text) ->
                    append(if (role == "user") "User: " else "LUMIQ: ")
                    append(text.take(600))
                    append('\n')
                }
                append('\n')
            }
            append(prompt)
        }

        val custom = customProviders.firstOrNull { it.id == activeProviderId }
        if (custom != null && custom.providerType != AiProviderType.FIREBASE_GEMINI &&
            custom.providerType != AiProviderType.GEMINI_FAST_LITE &&
            custom.providerType != AiProviderType.GEMINI_ARGON
        ) {
            val reply = callOpenAiCompatible(custom, enrichedPrompt)
            emit(reply)
            return@flow
        }

        val model = when (activeProviderId) {
            AiProviderType.GEMINI_FAST_LITE.name -> fastLiteModel
            AiProviderType.GEMINI_ARGON.name -> argonReasoningModel
            else -> defaultFlashModel
        }

        val inputContent = content {
            if (imageBitmap != null) {
                image(imageBitmap)
            }
            text(enrichedPrompt)
        }

        val sb = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(inputContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        sb.append(piece)
                        emit(sb.toString())
                    }
                }
            }
            if (sb.isBlank()) {
                throw IllegalStateException("Model returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            throw IllegalStateException("Request timed out after 60s. Please retry or switch to Flash-Lite.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic inference error", e)
            throw IllegalStateException(e.localizedMessage ?: "AI generation failed.")
        }
    }

    suspend fun generateOnce(
        prompt: String,
        activeProviderId: String = AiProviderType.GEMINI_FAST_LITE.name,
        customProviders: List<CustomProviderConfig> = emptyList(),
        imageBitmap: Bitmap? = null
    ): AiReplyResult {
        val start = System.currentTimeMillis()
        var finalText = ""
        streamReply(
            prompt = prompt,
            activeProviderId = activeProviderId,
            customProviders = customProviders,
            imageBitmap = imageBitmap
        ).collect { chunk ->
            finalText = chunk
        }
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        return AiReplyResult(
            text = finalText,
            latencyMs = elapsed,
            modelUsed = activeProviderId
        )
    }

    suspend fun testProviderConnection(config: CustomProviderConfig): Result<Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            if (config.providerType == AiProviderType.FIREBASE_GEMINI ||
                config.providerType == AiProviderType.GEMINI_FAST_LITE ||
                config.providerType == AiProviderType.GEMINI_ARGON
            ) {
                val model = when (config.providerType) {
                    AiProviderType.GEMINI_FAST_LITE -> fastLiteModel
                    AiProviderType.GEMINI_ARGON -> argonReasoningModel
                    else -> defaultFlashModel
                }
                val resp = withTimeout(25_000L) {
                    model.generateContent("Reply with OK only.")
                }
                if (resp.text.isNullOrBlank()) {
                    return@withContext Result.failure(IllegalStateException("Empty response from Firebase AI Logic"))
                }
                return@withContext Result.success(System.currentTimeMillis() - start)
            }

            val apiKey = KeystoreCryptoManager.decrypt(config.encryptedApiKey)
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("API Key is empty"))
            }
            if (!config.endpointUrl.startsWith("https://")) {
                return@withContext Result.failure(IllegalArgumentException("Endpoint URL must start with https://"))
            }

            val bodyJson = JSONObject().apply {
                put("model", config.modelName)
                put("max_tokens", 16)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", "Reply OK")
                        }
                    )
                )
            }
            val req = Request.Builder()
                .url(config.endpointUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${response.code}: ${raw.take(200)}")
                    )
                }
                Result.success(System.currentTimeMillis() - start)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun callOpenAiCompatible(
        config: CustomProviderConfig,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = KeystoreCryptoManager.decrypt(config.encryptedApiKey)
        require(apiKey.isNotBlank()) { "Missing API key for ${config.label}" }
        require(config.endpointUrl.startsWith("https://")) { "Endpoint must use HTTPS" }

        val bodyJson = JSONObject().apply {
            put("model", config.modelName)
            put("temperature", 0.5)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT_MAIN)
                    })
                    .put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
            )
        }

        val request = Request.Builder()
            .url(config.endpointUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Provider error HTTP ${response.code}: ${raw.take(240)}")
            }
            val json = JSONObject(raw)
            val choices = json.optJSONArray("choices")
            val contentText = choices?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (contentText.isBlank()) {
                throw IllegalStateException("Provider returned empty message.")
            }
            contentText
        }
    }

    private companion object {
        const val TAG = "LumiqAiEngine"
        const val SYSTEM_PROMPT_MAIN =
            "You are LUMIQ, the intelligent AI assistant of LUMIQ AI by LUMIQ LABS (Founder: Pranav Patel). " +
                "Be accurate, structured, fast, and helpful. Support English, Hindi, and Hinglish naturally. " +
                "Never fabricate tool outputs, live deployments, or device actions."
        const val SYSTEM_PROMPT_FAST =
            "You are LUMIQ in Ultra-Fast Voice & Instant Response mode (LUMIQ LABS). " +
                "Respond immediately with crisp, direct, natural answers in the user's language (English, Hindi, or Hinglish)."
    }
}
