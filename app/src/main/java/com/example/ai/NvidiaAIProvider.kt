package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.model.RepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class NvidiaAIProvider : AIProvider {
    override val providerName: String = "NVIDIA NIM"
    override val isAvailable: Boolean = true

    companion object {
        private const val TAG = "NvidiaAIProvider"
        const val DEFAULT_BASE_URL = "https://integrate.api.nvidia.com/v1"
    }

    suspend fun getApiKeyAndBaseUrl(): Pair<String, String> = withContext(Dispatchers.IO) {
        var key = ""
        var baseUrl = DEFAULT_BASE_URL
        try {
            val doc = RepositoryManager.firestore.collection("admin_settings")
                .document("ai_config").get().await()
            if (doc.exists()) {
                val dbKey = doc.getString("nvidia_key") ?: doc.getString("api_key")
                if (!dbKey.isNullOrBlank()) key = dbKey
                val dbUrl = doc.getString("base_url")
                if (!dbUrl.isNullOrBlank()) baseUrl = dbUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching AI config from Firestore: ${e.message}")
        }

        if (key.isBlank()) {
            key = try { BuildConfig.NVIDIA_API_KEY } catch (e: Exception) { "" }
        }
        if (key.isBlank()) {
            key = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        }

        Pair(key, baseUrl)
    }

    /**
     * Executes standard chat completion with model routing and structured request/response.
     */
    override suspend fun chat(
        messages: List<Pair<String, String>>, // role, content
        modelId: String?,
        temperature: Double,
        maxTokens: Int,
        imageUrl: String?
    ): String = withContext(Dispatchers.IO) {
        val (apiKey, baseUrl) = getApiKeyAndBaseUrl()
        if (apiKey.isBlank()) {
            throw AIException(AIErrorCode.AI_AUTH_ERROR, "NVIDIA API Key is not configured on the server.")
        }

        val targetModel = modelId ?: AIModelRegistry.getDefaultTextModel().id
        val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"

        val conn = try {
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 45000
                readTimeout = 45000
                doOutput = true
            }
        } catch (e: Exception) {
            throw AIException(AIErrorCode.AI_PROVIDER_UNAVAILABLE, "Unable to connect to NVIDIA NIM endpoint: ${e.message}")
        }

        val requestJson = JSONObject().apply {
            put("model", targetModel)
            put("max_tokens", maxTokens)
            put("temperature", temperature)

            val messagesArr = JSONArray()
            for ((role, content) in messages) {
                val msgObj = JSONObject().apply {
                    put("role", role)
                    if (role == "user" && !imageUrl.isNullOrBlank()) {
                        // Multimodal payload
                        val contentArr = JSONArray()
                        contentArr.put(JSONObject().apply {
                            put("type", "text")
                            put("text", content)
                        })
                        contentArr.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply { put("url", imageUrl) })
                        })
                        put("content", contentArr)
                    } else {
                        put("content", content)
                    }
                }
                messagesArr.put(msgObj)
            }
            put("messages", messagesArr)
        }

        try {
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseStr)
                val choices = root.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    val msg = choice.optJSONObject("message")
                    var text = msg?.optString("content", "") ?: ""
                    if (text.isBlank() || text == "null") {
                        text = msg?.optString("reasoning_content", "") ?: ""
                    }
                    if (text.isNotBlank() && text != "null") return@withContext text.trim()
                }
                return@withContext "No response generated."
            } else {
                val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                when (responseCode) {
                    401, 403 -> throw AIException(AIErrorCode.AI_AUTH_ERROR, "Invalid API credentials ($responseCode).")
                    429 -> throw AIException(AIErrorCode.AI_RATE_LIMIT, "Rate limit reached. Please wait a moment.")
                    503, 502 -> throw AIException(AIErrorCode.AI_PROVIDER_UNAVAILABLE, "NVIDIA NIM model is currently loading or unavailable.")
                    else -> throw AIException(AIErrorCode.AI_BACKEND_ERROR, "API error ($responseCode): $errorStream")
                }
            }
        } catch (e: AIException) {
            throw e
        } catch (e: Exception) {
            throw AIException(AIErrorCode.AI_TIMEOUT, "Request timed out or network disconnected: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Real-time streaming chat completions via SSE.
     */
    override fun chatStream(
        messages: List<Pair<String, String>>,
        modelId: String?,
        temperature: Double,
        maxTokens: Int
    ): Flow<String> = flow {
        val (apiKey, baseUrl) = getApiKeyAndBaseUrl()
        if (apiKey.isBlank()) {
            throw AIException(AIErrorCode.AI_AUTH_ERROR, "NVIDIA API Key not configured.")
        }

        val targetModel = modelId ?: AIModelRegistry.getDefaultTextModel().id
        val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "text/event-stream")
            connectTimeout = 30000
            readTimeout = 60000
            doOutput = true
        }

        val requestJson = JSONObject().apply {
            put("model", targetModel)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("stream", true)

            val messagesArr = JSONArray()
            for ((role, content) in messages) {
                messagesArr.put(JSONObject().apply {
                    put("role", role)
                    put("content", content)
                })
            }
            put("messages", messagesArr)
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestJson.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunkObj = JSONObject(data)
                        val choices = chunkObj.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val token = delta?.optString("content", "") ?: ""
                            if (token.isNotEmpty()) {
                                emit(token)
                            }
                        }
                    } catch (e: Exception) {
                        // ignore malformed heartbeat chunks
                    }
                }
                line = reader.readLine()
            }
            reader.close()
        } else {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw AIException(AIErrorCode.AI_BACKEND_ERROR, "Streaming error ($responseCode): $error")
        }
        conn.disconnect()
    }.flowOn(Dispatchers.IO)

    /**
     * Automatic Model Discovery via /v1/models endpoint.
     */
    override suspend fun discoverModels(): List<AIModel> = withContext(Dispatchers.IO) {
        val (apiKey, baseUrl) = getApiKeyAndBaseUrl()
        if (apiKey.isBlank()) return@withContext emptyList()

        val endpoint = "${baseUrl.trimEnd('/')}/models"
        try {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(resp)
                val data = root.optJSONArray("data") ?: JSONArray()
                val discovered = mutableListOf<AIModel>()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val id = item.optString("id", "")
                    if (id.isNotBlank()) {
                        val isVision = id.contains("vision") || id.contains("multimodal")
                        val isEmbedding = id.contains("embed")
                        val caps = mutableSetOf(AICapability.TEXT, AICapability.STREAMING)
                        if (isVision) {
                            caps.add(AICapability.VISION)
                            caps.add(AICapability.IMAGE_INPUT)
                        }
                        if (isEmbedding) {
                            caps.clear()
                            caps.add(AICapability.EMBEDDINGS)
                        }
                        val model = AIModel(
                            id = id,
                            name = id.split("/").lastOrNull() ?: id,
                            provider = "NVIDIA",
                            capabilities = caps,
                            isDefaultVision = isVision && discovered.none { it.isDefaultVision }
                        )
                        discovered.add(model)
                        AIModelRegistry.register(model)
                    }
                }
                return@withContext discovered
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model discovery failed: ${e.message}")
        }
        return@withContext emptyList()
    }
}
