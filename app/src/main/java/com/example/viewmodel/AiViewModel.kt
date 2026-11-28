package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.model.AiTool
import com.example.model.AiRequestHistory
import com.example.model.RepositoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiViewModel : ViewModel() {
    private val _aiTools = MutableStateFlow<List<AiTool>>(emptyList())
    val aiTools: StateFlow<List<AiTool>> = _aiTools.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _requestState = MutableStateFlow<RequestState>(RequestState.Idle)
    val requestState = _requestState.asStateFlow()

    private val _history = MutableStateFlow<List<AiRequestHistory>>(emptyList())
    val history = _history.asStateFlow()

    sealed class RequestState {
        object Idle : RequestState()
        object Loading : RequestState()
        data class Success(val result: String, val isMedia: Boolean = false) : RequestState()
        data class Error(val message: String) : RequestState()
    }

    init {
        fetchAiTools()
    }

    fun fetchAiTools() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = RepositoryManager.firestore.collection("ai_tools").orderBy("order").get().await()
                val firestoreTools = snapshot.documents.mapNotNull { it.toObject(AiTool::class.java)?.copy(id = it.id) }
                
                // Convert AIFeatures from Registry to AiTools
                val registryTools = com.example.ai.AIFeatureRegistry.getAllFeatures().map { feat ->
                    val isAvailable = com.example.ai.AIFeatureRegistry.isFeatureAvailable(feat.featureId)
                    AiTool(
                        id = feat.featureId,
                        name = feat.name,
                        description = feat.description,
                        category = feat.category,
                        status = if (isAvailable) "Active" else "Disabled",
                        modelIdentifier = "meta/llama-3.2-11b-vision-instruct",
                        supportedInput = if (feat.requiredCapabilities.contains(com.example.ai.AICapability.IMAGE_INPUT)) "image" else "text",
                        supportedOutput = "text",
                        requiresKey = true
                    )
                }

                // Combine: Firestore tools take precedence, but include default registry tools
                val combined = mutableListOf<AiTool>()
                combined.addAll(firestoreTools)
                for (regTool in registryTools) {
                    if (combined.none { it.id == regTool.id }) {
                        combined.add(regTool)
                    }
                }

                _aiTools.value = combined
            } catch (e: Exception) {
                // If firestore fails, fallback to standard registry
                val fallbackTools = com.example.ai.AIFeatureRegistry.getAllFeatures().map { feat ->
                    AiTool(
                        id = feat.featureId,
                        name = feat.name,
                        description = feat.description,
                        category = feat.category,
                        status = "Active",
                        modelIdentifier = "meta/llama-3.2-11b-vision-instruct"
                    )
                }
                _aiTools.value = fallbackTools
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchHistory() {
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val snapshot = RepositoryManager.firestore.collection("ai_history")
                    .whereEqualTo("userId", uid)
                    .get().await()
                val hist = snapshot.documents.mapNotNull { it.toObject(AiRequestHistory::class.java)?.copy(id = it.id) }
                    .sortedByDescending { it.timestamp }
                _history.value = hist
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun executeAiRequest(tool: AiTool, input: String, mediaUrl: String? = null) {
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isEmpty()) {
            _requestState.value = RequestState.Error("Authentication Error: Please login.")
            return
        }

        if (tool.status != "Active") {
            _requestState.value = RequestState.Error("This AI tool is currently ${tool.status}.")
            return
        }

        if (input.isBlank() && mediaUrl == null) {
            _requestState.value = RequestState.Error("Input cannot be empty.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _requestState.value = RequestState.Loading
            try {
                val (geminiKey, nvidiaKey) = fetchApiKeys()
                
                val result = when {
                    // If tool specifically targets NVIDIA or has an nvapi key
                    tool.apiEndpoint.contains("nvidia") || tool.modelIdentifier.startsWith("meta/") || 
                    tool.modelIdentifier.startsWith("nvidia/") || tool.modelIdentifier.startsWith("deepseek") -> {
                        val key = if (nvidiaKey.isNotBlank()) nvidiaKey else geminiKey
                        if (key.isBlank() && tool.requiresKey) throw Exception("Server Configuration Error: NVIDIA API key missing.")
                        callNvidiaApi(tool, input, key)
                    }
                    // If NVIDIA key is available but Gemini key is not, route through NVIDIA NIM
                    geminiKey.isBlank() && nvidiaKey.isNotBlank() -> {
                        callNvidiaApi(tool, input, nvidiaKey)
                    }
                    // If the available key is an NVIDIA key
                    geminiKey.startsWith("nvapi-") -> {
                        callNvidiaApi(tool, input, geminiKey)
                    }
                    // Otherwise default to Gemini
                    else -> {
                        val key = if (geminiKey.isNotBlank()) geminiKey else nvidiaKey
                        if (key.isBlank() && tool.requiresKey) throw Exception("Server Configuration Error: API key missing.")
                        if (key.startsWith("nvapi-")) {
                            callNvidiaApi(tool, input, key)
                        } else {
                            callGeminiApi(tool, input, key)
                        }
                    }
                }
                
                // Save history
                val hist = AiRequestHistory(
                    aiId = tool.id,
                    aiName = tool.name,
                    userId = uid,
                    prompt = input,
                    resultText = result,
                    resultMediaUrl = ""
                )
                val docRef = RepositoryManager.firestore.collection("ai_history").document()
                hist.id = docRef.id
                docRef.set(hist)
                
                withContext(Dispatchers.Main) {
                    _requestState.value = RequestState.Success(result)
                    fetchHistory()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _requestState.value = RequestState.Error(e.message ?: "Network error or API timeout.")
                }
            }
        }
    }
    
    private suspend fun fetchApiKeys(): Pair<String, String> {
        var gKey = ""
        var nKey = ""
        try {
            val doc = RepositoryManager.firestore.collection("admin_settings").document("ai_config").get().await()
            gKey = doc.getString("gemini_key") ?: ""
            nKey = doc.getString("nvidia_key") ?: ""
        } catch (e: Exception) {
            // ignore
        }
        
        if (gKey.isBlank()) {
            gKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        }
        if (nKey.isBlank()) {
            nKey = try { BuildConfig.NVIDIA_API_KEY } catch (e: Exception) { "" }
        }
        return Pair(gKey, nKey)
    }

    private fun callNvidiaApi(tool: AiTool, input: String, apiKey: String): String {
        val model = when {
            tool.modelIdentifier.isNotBlank() && !tool.modelIdentifier.contains("gemini") -> tool.modelIdentifier
            else -> "meta/llama-3.2-11b-vision-instruct"
        }
        val endpoint = if (tool.apiEndpoint.isNotBlank() && tool.apiEndpoint.contains("http")) {
            tool.apiEndpoint
        } else {
            "https://integrate.api.nvidia.com/v1/chat/completions"
        }

        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        conn.doOutput = true

        val requestBody = JSONObject()
        requestBody.put("model", model)
        val messagesArray = org.json.JSONArray()
        val userMsg = JSONObject()
        userMsg.put("role", "user")
        userMsg.put("content", input)
        messagesArray.put(userMsg)
        requestBody.put("messages", messagesArray)
        requestBody.put("max_tokens", 1024)
        requestBody.put("temperature", 0.7)

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseString = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseString)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                var content = message?.optString("content", "") ?: ""
                if (content.isBlank() || content == "null") {
                    content = message?.optString("reasoning_content", "") ?: ""
                }
                if (content.isNotBlank() && content != "null") return content
            }
            return "No response generated."
        } else {
            val errorString = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("NVIDIA API Error ($responseCode): $errorString")
        }
    }

    private fun callGeminiApi(tool: AiTool, input: String, apiKey: String): String {
        val model = if (tool.modelIdentifier.isNotEmpty()) tool.modelIdentifier else "gemini-1.5-flash"
        val endpoint = if (tool.apiEndpoint.isNotEmpty()) tool.apiEndpoint else "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val requestBody = JSONObject()
        val contentsArray = org.json.JSONArray()
        val partsArray = org.json.JSONArray()
        val partObj = JSONObject()
        partObj.put("text", input)
        partsArray.put(partObj)
        
        val contentObj = JSONObject()
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        
        requestBody.put("contents", contentsArray)

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseString = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseString)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text", "No text generated.")
                }
            }
            return "Empty response from AI."
        } else {
            val errorString = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("API Error ($responseCode): $errorString")
        }
    }
    
    fun resetRequestState() {
        _requestState.value = RequestState.Idle
    }
}
