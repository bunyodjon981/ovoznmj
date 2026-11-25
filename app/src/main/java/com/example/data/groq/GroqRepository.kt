package com.example.data.groq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GroqToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class GroqChatResult(
    val content: String?,
    val toolCalls: List<GroqToolCall>,
    val modelUsed: String,
    val errorMessage: String? = null
)

data class GroqModelsResult(
    val isSuccess: Boolean,
    val models: List<String>,
    val bestModel: String,
    val errorMessage: String? = null
)

class GroqRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1"

        val DEFAULT_PRIORITY_MODELS = listOf(
            "llama-3.3-70b-versatile",
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "meta-llama/llama-4-maverick-17b-16e-instruct",
            "qwen/qwen3-32b",
            "llama-3.1-8b-instant",
            "gemma2-9b-it"
        )
    }

    suspend fun fetchAvailableFreeModels(apiKey: String): GroqModelsResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext GroqModelsResult(
                isSuccess = false,
                models = emptyList(),
                bestModel = DEFAULT_PRIORITY_MODELS.first(),
                errorMessage = "API kaliti kiritilmagan."
            )
        }

        val request = Request.Builder()
            .url("$BASE_URL/models")
            .addHeader("Authorization", "Bearer $trimmedKey")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = parseGroqError(bodyString, response.code)
                    return@withContext GroqModelsResult(
                        isSuccess = false,
                        models = emptyList(),
                        bestModel = DEFAULT_PRIORITY_MODELS.first(),
                        errorMessage = err
                    )
                }

                val root = JSONObject(bodyString)
                val dataArray = root.optJSONArray("data") ?: JSONArray()
                val rawIds = mutableListOf<String>()
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val active = item.optBoolean("active", true)
                    if (active && id.isNotBlank() && isChatCapableModel(id)) {
                        rawIds.add(id)
                    }
                }

                val sorted = sortModelsByPriority(rawIds)
                val best = sorted.firstOrNull() ?: DEFAULT_PRIORITY_MODELS.first()
                GroqModelsResult(
                    isSuccess = true,
                    models = sorted,
                    bestModel = best
                )
            }
        } catch (e: Exception) {
            GroqModelsResult(
                isSuccess = false,
                models = emptyList(),
                bestModel = DEFAULT_PRIORITY_MODELS.first(),
                errorMessage = "Internet yoki ulanish xatosi: ${e.localizedMessage ?: "noma'lum"}"
            )
        }
    }

    suspend fun chatWithTools(
        apiKey: String,
        preferredModel: String,
        candidateModels: List<String>,
        autoFallback: Boolean,
        messages: JSONArray,
        tools: JSONArray?
    ): GroqChatResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext GroqChatResult(
                content = null,
                toolCalls = emptyList(),
                modelUsed = preferredModel,
                errorMessage = "Groq API kaliti kiritilmagan."
            )
        }

        val modelsToTry = buildList {
            if (preferredModel.isNotBlank()) add(preferredModel)
            if (autoFallback) {
                candidateModels.forEach { if (it !in this) add(it) }
                DEFAULT_PRIORITY_MODELS.forEach { if (it !in this) add(it) }
            }
        }.distinct()

        var lastError = "Groq AI javob bermadi."
        for (modelId in modelsToTry) {
            val attempt = executeChatCall(
                apiKey = trimmedKey,
                modelId = modelId,
                messages = messages,
                tools = tools
            )
            if (attempt.errorMessage == null) {
                return@withContext attempt
            }
            lastError = attempt.errorMessage
            // If unauthorized (401), don't retry other models
            if (lastError.contains("401") || lastError.contains("kaliti noto'g'ri")) {
                return@withContext attempt
            }
            if (!autoFallback) {
                return@withContext attempt
            }
        }

        GroqChatResult(
            content = null,
            toolCalls = emptyList(),
            modelUsed = preferredModel,
            errorMessage = lastError
        )
    }

    private fun executeChatCall(
        apiKey: String,
        modelId: String,
        messages: JSONArray,
        tools: JSONArray?
    ): GroqChatResult {
        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messages)
            put("temperature", 0.3)
            put("max_tokens", 1024)
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
        }

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return GroqChatResult(
                        content = null,
                        toolCalls = emptyList(),
                        modelUsed = modelId,
                        errorMessage = parseGroqError(bodyString, response.code)
                    )
                }

                val root = JSONObject(bodyString)
                val choices = root.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return GroqChatResult(
                        content = null,
                        toolCalls = emptyList(),
                        modelUsed = modelId,
                        errorMessage = "Model bo'sh javob qaytardi."
                    )
                }

                val message = choices.getJSONObject(0).getJSONObject("message")
                val content = if (message.isNull("content")) null else message.optString("content")
                val rawToolCalls = message.optJSONArray("tool_calls")
                val parsedToolCalls = mutableListOf<GroqToolCall>()

                if (rawToolCalls != null) {
                    for (i in 0 until rawToolCalls.length()) {
                        val tc = rawToolCalls.optJSONObject(i) ?: continue
                        val id = tc.optString("id", "call_$i")
                        val fn = tc.optJSONObject("function") ?: continue
                        val fnName = fn.optString("name")
                        val fnArgs = fn.optString("arguments", "{}")
                        if (fnName.isNotBlank()) {
                            parsedToolCalls.add(GroqToolCall(id, fnName, fnArgs))
                        }
                    }
                }

                GroqChatResult(
                    content = content,
                    toolCalls = parsedToolCalls,
                    modelUsed = modelId
                )
            }
        } catch (e: Exception) {
            GroqChatResult(
                content = null,
                toolCalls = emptyList(),
                modelUsed = modelId,
                errorMessage = "Tarmoq xatosi: ${e.localizedMessage ?: "ulana olmadi"}"
            )
        }
    }

    private fun isChatCapableModel(id: String): Boolean {
        val lower = id.lowercase()
        val excluded = listOf(
            "whisper", "tts", "guard", "embed", "rerank", "moderation", "audio", "orpheus"
        )
        return excluded.none { lower.contains(it) }
    }

    private fun sortModelsByPriority(available: List<String>): List<String> {
        val prioritySet = DEFAULT_PRIORITY_MODELS
        val prioritized = prioritySet.filter { it in available }
        val others = available.filter { it !in prioritySet }.sorted()
        return prioritized + others
    }

    private fun parseGroqError(body: String, code: Int): String {
        val msg = try {
            JSONObject(body).optJSONObject("error")?.optString("message")
        } catch (_: Exception) {
            null
        }
        return when (code) {
            401 -> "Groq API kaliti noto'g'ri (401). Iltimos kalitni tekshiring."
            429 -> "Model limiti to'ldi (429), boshqa bepul modelga o'tilmoqda..."
            else -> "Groq xatosi ($code): ${msg ?: "Noma'lum xatolik"}"
        }
    }
}
