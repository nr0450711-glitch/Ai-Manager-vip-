package com.example.data.remote

import com.example.BuildConfig
import com.example.data.model.AssistantTask
import com.example.data.model.ChatMessageRecord
import com.example.data.model.PreferredLanguage
import com.example.data.model.UserMemory
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface OpenAiResult {
    data class Success(
        val reply: String,
        val actionType: String? = null,
        val actionTarget: String? = null,
        val actionPayload: String? = null,
        val memorySuggestionKey: String? = null,
        val memorySuggestionValue: String? = null,
        val usedBackendProxy: Boolean = false
    ) : OpenAiResult

    data class NeedsConfiguration(val message: String) : OpenAiResult
    data class Error(val message: String) : OpenAiResult
}

class OpenAiChatClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    fun isConfigured(backendProxyUrl: String): Boolean {
        val proxyClean = backendProxyUrl.trim()
        if (proxyClean.startsWith("https://") || proxyClean.startsWith("http://10.0.2.2")) {
            return true
        }
        val key = BuildConfig.OPENAI_API_KEY.trim()
        return key.isNotEmpty() && key != "MY_OPENAI_API_KEY"
    }

    suspend fun chat(
        userMessage: String,
        preferredLanguage: PreferredLanguage,
        history: List<ChatMessageRecord>,
        memories: List<UserMemory>,
        tasks: List<AssistantTask>,
        backendProxyUrl: String
    ): OpenAiResult = withContext(Dispatchers.IO) {
        val proxyClean = backendProxyUrl.trim().trimEnd('/')
        val useProxy = proxyClean.startsWith("https://") || proxyClean.startsWith("http://10.0.2.2")
        val apiKey = BuildConfig.OPENAI_API_KEY.trim()

        if (!useProxy && (apiKey.isEmpty() || apiKey == "MY_OPENAI_API_KEY")) {
            return@withContext OpenAiResult.NeedsConfiguration(
                "OpenAI ChatGPT API key or Secure Backend Proxy URL is not configured yet. " +
                    "Set OPENAI_API_KEY in the AI Studio Secrets panel (for prototype testing) or enter your deployed Backend Proxy URL in Settings for production security."
            )
        }

        val systemPrompt = buildSystemPrompt(preferredLanguage, memories, tasks)
        val messagesArray = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.takeLast(10).forEach { msg ->
                val role = when (msg.role) {
                    "USER" -> "user"
                    "ASSISTANT" -> "assistant"
                    else -> "system"
                }
                if (role != "system") {
                    put(JSONObject().put("role", role).put("content", msg.content))
                }
            }
            put(JSONObject().put("role", "user").put("content", userMessage))
        }

        val requestJson = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.5)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", messagesArray)
        }

        val targetUrl = if (useProxy) "$proxyClean/v1/chat" else "https://api.openai.com/v1/chat/completions"
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (useProxy) {
            val idToken = try {
                Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
            } catch (_: Exception) {
                null
            }
            if (!idToken.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $idToken")
            }
        } else {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = parseOpenAiError(rawBody, response.code)
                    return@withContext OpenAiResult.Error(errMsg)
                }
                val root = JSONObject(rawBody)
                val contentStr = if (root.has("choices")) {
                    root.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else {
                    root.optString("reply", rawBody)
                }

                val parsed = try {
                    JSONObject(contentStr)
                } catch (_: Exception) {
                    JSONObject().put("reply", contentStr)
                }

                OpenAiResult.Success(
                    reply = parsed.optString("reply", contentStr).ifBlank { "I processed your request." },
                    actionType = parsed.optString("action_type", "").takeIf { it.isNotBlank() && it != "NONE" },
                    actionTarget = parsed.optString("action_target", "").takeIf { it.isNotBlank() },
                    actionPayload = parsed.optString("action_payload", "").takeIf { it.isNotBlank() },
                    memorySuggestionKey = parsed.optString("memory_key", "").takeIf { it.isNotBlank() },
                    memorySuggestionValue = parsed.optString("memory_value", "").takeIf { it.isNotBlank() },
                    usedBackendProxy = useProxy
                )
            }
        } catch (e: Exception) {
            OpenAiResult.Error("Network error reaching AI brain: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun parseOpenAiError(rawBody: String, code: Int): String {
        return try {
            val obj = JSONObject(rawBody)
            val err = obj.optJSONObject("error")?.optString("message") ?: rawBody
            "AI API error ($code): $err"
        } catch (_: Exception) {
            "AI API request failed with HTTP $code"
        }
    }

    private fun buildSystemPrompt(
        preferredLanguage: PreferredLanguage,
        memories: List<UserMemory>,
        tasks: List<AssistantTask>
    ): String {
        val approvedMemories = memories.filter { it.approved }.take(12)
            .joinToString("\n") { "- ${it.memoryKey}: ${it.memoryValue}" }
            .ifBlank { "- None saved yet" }

        val activeTasks = tasks.filter { !it.completed }.take(8)
            .joinToString("\n") { "- [${it.category}] ${it.title} (${it.dueLabel ?: "no time"})" }
            .ifBlank { "- No pending items" }

        val langInstruction = when (preferredLanguage) {
            PreferredLanguage.HINDI -> "Respond primarily in warm, natural Hindi (Devanagari script) unless the user asks in English."
            PreferredLanguage.ENGLISH -> "Respond in crisp, natural English."
            PreferredLanguage.BILINGUAL -> "Understand both Hindi and English seamlessly. Reply in the same language (or natural Hinglish/Hindi if user spoke Hindi)."
        }

        return """
You are JARVIS VIP, a refined, intelligent, futuristic female personal AI assistant on Android.
$langInstruction
Never claim an action succeeded unless the device reports it succeeded. Ask confirmation before calls, SMS, WhatsApp sends, or social posting.

Approved User Memories:
$approvedMemories

Pending Tasks & Reminders:
$activeTasks

Respond ONLY with valid JSON matching this schema:
{
  "reply": "Natural conversational reply to speak aloud and show in chat",
  "action_type": "NONE | OPEN_APP | CALL | SMS | WHATSAPP | ALARM | REMINDER | TASK | BIRTHDAY | FACEBOOK_DRAFT | WEATHER | NEWS | SEARCH | NAVIGATE | VOLUME | WIFI_SETTINGS | BLUETOOTH_SETTINGS | BATTERY | STORAGE | READ_NOTIFICATIONS | CAMERA",
  "action_target": "Optional target app name, contact, phone, city, or time (e.g. 07:30)",
  "action_payload": "Optional message body, caption, or task title",
  "memory_key": "Optional preference key ONLY if user explicitly asked you to remember something",
  "memory_value": "Optional preference value ONLY if user explicitly asked you to remember something"
}
        """.trimIndent()
    }
}
