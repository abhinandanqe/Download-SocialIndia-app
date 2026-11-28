package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AdminAiConfig
import com.example.ai.MahiAiEngine
import com.example.model.AiTool
import com.example.model.RepositoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminAiViewModel : ViewModel() {
    private val _aiTools = MutableStateFlow<List<AiTool>>(emptyList())
    val aiTools: StateFlow<List<AiTool>> = _aiTools.asStateFlow()

    private val _adminConfig = MutableStateFlow(AdminAiConfig())
    val adminConfig: StateFlow<AdminAiConfig> = _adminConfig.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        loadAdminConfig()
        fetchAiTools()
    }

    fun loadAdminConfig() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val cfg = MahiAiEngine.fetchAdminAiConfig()
                _adminConfig.value = cfg
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveAdminAiConfig(
        aiName: String,
        aiAvatarUrl: String,
        systemPrompt: String,
        personaMode: String,
        customQaList: List<Pair<String, String>>,
        geminiKey: String,
        nvidiaKey: String,
        sarvamKey: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val qaMapList = customQaList.map { mapOf("q" to it.first, "a" to it.second) }
                val data = mapOf(
                    "ai_name" to aiName,
                    "ai_avatar_url" to aiAvatarUrl,
                    "system_prompt" to systemPrompt,
                    "persona_mode" to personaMode,
                    "custom_qa" to qaMapList,
                    "gemini_key" to geminiKey,
                    "nvidia_key" to nvidiaKey,
                    "sarvam_key" to sarvamKey
                )
                RepositoryManager.firestore.collection("admin_settings").document("ai_config").set(data).await()
                MahiAiEngine.cachedAdminConfig = null
                loadAdminConfig()
                _message.value = "AI Identity & Training saved successfully!"
            } catch (e: Exception) {
                _message.value = "Failed to save AI config: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchAiTools() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = RepositoryManager.firestore.collection("ai_tools").orderBy("order").get().await()
                val tools = snapshot.documents.mapNotNull { it.toObject(AiTool::class.java)?.copy(id = it.id) }
                _aiTools.value = tools
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveAiTool(tool: AiTool) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val collection = RepositoryManager.firestore.collection("ai_tools")
                if (tool.id.isEmpty()) {
                    val doc = collection.document()
                    tool.id = doc.id
                    doc.set(tool).await()
                    _message.value = "AI Tool created successfully."
                } else {
                    collection.document(tool.id).set(tool).await()
                    _message.value = "AI Tool updated successfully."
                }
                fetchAiTools()
            } catch (e: Exception) {
                _message.value = "Failed to save AI tool: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteAiTool(toolId: String) {
        viewModelScope.launch {
            try {
                RepositoryManager.firestore.collection("ai_tools").document(toolId).delete().await()
                _message.value = "AI Tool deleted successfully."
                fetchAiTools()
            } catch (e: Exception) {
                _message.value = "Failed to delete AI tool: ${e.message}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
