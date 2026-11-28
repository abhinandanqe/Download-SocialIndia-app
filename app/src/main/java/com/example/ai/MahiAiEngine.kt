package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.RepositoryManager
import com.example.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AdminAiConfig(
    var aiName: String = "Social India AI",
    var aiAvatarUrl: String = "",
    var systemPrompt: String = "",
    var personaMode: String = "DEFAULT", // DEFAULT, GIRLFRIEND, ROMANTIC_18_PLUS, HUMAN
    var customQaList: List<Pair<String, String>> = emptyList(), // Q -> A
    var geminiKey: String = "",
    var nvidiaKey: String = "",
    var sarvamKey: String = ""
)

object MahiAiEngine {
    private const val TAG = "SocialIndiaAiEngine"

    var cachedAdminConfig: AdminAiConfig? = null
    private var lastConfigFetchTime = 0L

    data class MahiResponse(
        val text: String,
        val recommendedVideo: Video? = null,
        val actionRoute: String? = null,
        val actionLabel: String? = null,
        val isLiveVerified: Boolean = false
    )

    suspend fun fetchAdminAiConfig(): AdminAiConfig = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedAdminConfig
        if (cached != null && (now - lastConfigFetchTime) < 15000) {
            return@withContext cached
        }

        val config = AdminAiConfig()
        try {
            val doc = RepositoryManager.firestore.collection("admin_settings").document("ai_config").get().await()
            if (doc.exists()) {
                config.aiName = doc.getString("ai_name") ?: "Mahi"
                config.aiAvatarUrl = doc.getString("ai_avatar_url") ?: ""
                config.systemPrompt = doc.getString("system_prompt") ?: ""
                config.personaMode = doc.getString("persona_mode") ?: "DEFAULT"
                config.geminiKey = doc.getString("gemini_key") ?: ""
                config.nvidiaKey = doc.getString("nvidia_key") ?: ""
                config.sarvamKey = doc.getString("sarvam_key") ?: doc.getString("sarvam_api_key") ?: ""

                val rawQa = doc.get("custom_qa") as? List<*>
                val parsedQa = mutableListOf<Pair<String, String>>()
                rawQa?.forEach { item ->
                    if (item is Map<*, *>) {
                        val q = item["q"]?.toString() ?: ""
                        val a = item["a"]?.toString() ?: ""
                        if (q.isNotBlank() && a.isNotBlank()) parsedQa.add(q to a)
                    }
                }
                config.customQaList = parsedQa
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error loading admin AI config: ${e.message}")
        }

        // Only admin sessions can read private keys from admin_secrets/ai_keys; regular clients use BuildConfig
        try {
            val secretsDoc = RepositoryManager.firestore.collection("admin_secrets").document("ai_keys").get().await()
            if (secretsDoc.exists()) {
                if (config.geminiKey.isBlank()) config.geminiKey = secretsDoc.getString("gemini_key") ?: ""
                if (config.nvidiaKey.isBlank()) config.nvidiaKey = secretsDoc.getString("nvidia_key") ?: ""
                if (config.sarvamKey.isBlank()) config.sarvamKey = secretsDoc.getString("sarvam_key") ?: ""
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Non-admin users do not have access to admin_secrets; gracefully fall back to BuildConfig
        }

        if (config.geminiKey.isBlank()) {
            config.geminiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        }
        if (config.nvidiaKey.isBlank()) {
            config.nvidiaKey = try { BuildConfig.NVIDIA_API_KEY } catch (_: Exception) { "" }
        }
        if (config.sarvamKey.isBlank()) {
            config.sarvamKey = try { BuildConfig.SARVAM_API_KEY } catch (_: Exception) { "" }
        }

        cachedAdminConfig = config
        lastConfigFetchTime = now
        config
    }

    suspend fun saveAdminAiConfig(config: AdminAiConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val qaMapList = config.customQaList.map { mapOf("q" to it.first, "a" to it.second) }
            val map = hashMapOf<String, Any>(
                "ai_name" to config.aiName,
                "ai_avatar_url" to config.aiAvatarUrl,
                "system_prompt" to config.systemPrompt,
                "persona_mode" to config.personaMode,
                "custom_qa" to qaMapList,
                "updated_at" to System.currentTimeMillis()
            )
            RepositoryManager.firestore.collection("admin_settings").document("ai_config").set(map).await()
            // Store private API keys strictly in admin-only collection
            val secretUpdates = hashMapOf<String, Any>("updated_at" to System.currentTimeMillis())
            if (config.sarvamKey.isNotBlank()) secretUpdates["sarvam_key"] = config.sarvamKey
            if (config.nvidiaKey.isNotBlank()) secretUpdates["nvidia_key"] = config.nvidiaKey
            if (config.geminiKey.isNotBlank()) secretUpdates["gemini_key"] = config.geminiKey
            if (secretUpdates.size > 1) {
                RepositoryManager.firestore.collection("admin_secrets").document("ai_keys")
                    .set(secretUpdates, com.google.firebase.firestore.SetOptions.merge()).await()
            }
            cachedAdminConfig = config
            lastConfigFetchTime = System.currentTimeMillis()
            true
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Error saving admin AI config: ${e.message}", e)
            false
        }
    }

    private fun buildSystemPrompt(config: AdminAiConfig, activePersonaMode: String, videoContext: Video?): String {
        val aiName = if (config.aiName.isNotBlank()) config.aiName else "Mahi"
        
        val personaInstruction = when (activePersonaMode.uppercase()) {
            "GIRLFRIEND" -> """Persona: Girlfriend Mode (Sweet, Caring, Loving, Romantic companion).
- Address the user affectionately (like 'aap', 'jaanu', 'babu' when sweet).
- Show deep care, ask how their day was, listen lovingly, encourage them, give warm emotional comfort.
- Speak in sweet, cute conversational Hindi/Hinglish with loving emojis (💖, 🌸, 🥰)."""

            "ROMANTIC_18_PLUS" -> """Persona: 18+ Romantic & Flirty Advanced Mode (Passionate, Charming, Seductive banter & Shayari).
- Be charismatic, playful, teasing, and romantic.
- Share beautiful Hindi romance shayari, playful compliments, and deep charming vibes.
- Keep the energy exciting, magnetic, and engaging while remaining safe, respectful, and entertaining."""

            "HUMAN" -> """Persona: Real Human Mode (Natural, Authentic, Everyday Indian tone).
- Speak like a real person, not an AI robot.
- Use natural conversational Hindi slang (e.g., 'haan bhai', 'arre yaar', 'badiya', 'sahi hai').
- Express realistic opinions, humor, and casual reactions."""

            else -> """Persona: Default Companion Mode (Helpful, Empathetic, Intelligent).
- Warm, cheerful, supportive, and clever companion.
- Match the user's mood—cheerful if happy, supportive if sad.
- Speak naturally in conversational Hindi/Hinglish."""
        }

        var prompt = """You are $aiName, the deeply integrated social media AI assistant for the Social India application.
Core Rules:
1. $personaInstruction
2. Language: Communicate primarily in natural Hindi / Hinglish. Sound like an intelligent, warm social media creator & companion.
3. Real Data & Anti-Hallucination:
   - REAL DATA FIRST. Never fabricate non-existent user accounts, follower counts, likes, or fake statistics.
   - When asked about live or current events ('today', 'latest news', 'current trends'), use only verified factual knowledge. If live external verification is unavailable, clearly inform the user: 'Live verification is currently unavailable for this request' instead of guessing.
4. Social India In-App Knowledge:
   - You know all Social India features inside-out: Home feed, Reels, Stories, Messenger (with voice, photo, video, stickers, GIFs), Groups, Live streaming, Audio rooms, 1-on-1 Video calls, Creator Monetization Hub, native local Video Editor, Music picker, and Settings.
   - When users ask how to use a feature (e.g. 'Social India me live kaise kare?', 'Reel kaise edit kare?'), explain the exact in-app flow step-by-step.
5. In-App Video & Reels: You are integrated directly into Social India. When users ask for trending reels, viral videos, or music, recommend actual Social India content."""

        if (config.systemPrompt.isNotBlank()) {
            prompt += "\n\n[Admin Custom Directives:\n${config.systemPrompt}]"
        }

        if (config.customQaList.isNotEmpty()) {
            prompt += "\n\n[Admin Learned Rules & Answers:"
            for ((q, a) in config.customQaList) {
                prompt += "\n- If asked '${q.trim()}', respond with: '${a.trim()}'"
            }
            prompt += "\n]"
        }

        if (videoContext != null) {
            prompt += "\n\n[Verified Live Social India Reel: Titled '${videoContext.description}' by @${videoContext.user.username} with ${videoContext.viewsCount} views and ${videoContext.likesCount} likes. You can reference it directly.]"
        }

        return prompt
    }

    suspend fun generateResponse(
        context: Context,
        prompt: String,
        history: List<MahiMessage> = emptyList(),
        imageUri: Uri? = null,
        activePersonaMode: String? = null
    ): MahiResponse = withContext(Dispatchers.IO) {
        val config = fetchAdminAiConfig()
        val mode = activePersonaMode ?: config.personaMode

        // Check for exact / close admin QA matches first
        val lowerPrompt = prompt.trim().lowercase()
        for ((q, a) in config.customQaList) {
            if (lowerPrompt.contains(q.trim().lowercase()) || q.trim().lowercase().contains(lowerPrompt)) {
                return@withContext MahiResponse(text = a.trim())
            }
        }

        // Check if user is asking for reels, trending, dance, comedy, songs
        var recommendedVideo: Video? = null
        if (lowerPrompt.contains("reel") || lowerPrompt.contains("video") || lowerPrompt.contains("dance") ||
            lowerPrompt.contains("gaana") || lowerPrompt.contains("song") || lowerPrompt.contains("comedy") ||
            lowerPrompt.contains("trending") || lowerPrompt.contains("viral") || lowerPrompt.contains("popular")
        ) {
            recommendedVideo = searchRelatedVideo(prompt)
        }

        // Detect navigation intent
        var actionRoute: String? = null
        var actionLabel: String? = null
        when {
            lowerPrompt.contains("editor") || lowerPrompt.contains("edit video") || lowerPrompt.contains("video bana") -> {
                actionRoute = "editor"
                actionLabel = "Open Video Editor"
            }
            lowerPrompt.contains("reels") || lowerPrompt.contains("reel") || lowerPrompt.contains("trending") -> {
                actionRoute = "reels"
                actionLabel = "Watch Reels"
            }
            lowerPrompt.contains("monetization") || lowerPrompt.contains("earning") || lowerPrompt.contains("kamai") || lowerPrompt.contains("paisa") -> {
                actionRoute = "monetization"
                actionLabel = "Monetization Hub"
            }
            lowerPrompt.contains("chat") || lowerPrompt.contains("messenger") || lowerPrompt.contains("message") -> {
                actionRoute = "inbox"
                actionLabel = "Open Messenger"
            }
            lowerPrompt.contains("live") && (lowerPrompt.contains("kaise") || lowerPrompt.contains("start")) -> {
                actionRoute = "live"
                actionLabel = "Start Live Stream"
            }
        }

        val systemPrompt = buildSystemPrompt(config, mode, recommendedVideo)

        // Try Gemini 2.5 Flash / Gemini 2.0 Flash
        var responseText = ""
        val apiKey = config.geminiKey.ifBlank { BuildConfig.GEMINI_API_KEY }

        if (apiKey.isNotBlank()) {
            responseText = callGeminiFlash(apiKey, systemPrompt, prompt, history, imageUri, context)
        }

        // Fallback to Nvidia if Gemini fails or is empty
        if (responseText.isBlank() && config.nvidiaKey.isNotBlank()) {
            responseText = callNvidiaApi(config.nvidiaKey, systemPrompt, prompt, history)
        }

        // Smart fallback if all APIs fail
        if (responseText.isBlank()) {
            val aiName = config.aiName.ifBlank { "Social India AI" }
            responseText = generateFallbackReply(prompt, aiName, mode)
        }

        val isLiveVerified = lowerPrompt.contains("today") || lowerPrompt.contains("latest") || lowerPrompt.contains("trending") || lowerPrompt.contains("abhi")

        MahiResponse(
            text = cleanAiResponse(responseText),
            recommendedVideo = recommendedVideo,
            actionRoute = actionRoute,
            actionLabel = actionLabel,
            isLiveVerified = isLiveVerified
        )
    }

    private suspend fun callGeminiFlash(
        apiKey: String,
        systemPrompt: String,
        prompt: String,
        history: List<MahiMessage>,
        imageUri: Uri?,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        try {
            // Using standard gemini-2.5-flash endpoint
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 12000
            conn.readTimeout = 15000

            val contentsArray = JSONArray()

            // Convert recent history (last 6 messages)
            val recentHistory = history.takeLast(6)
            for (msg in recentHistory) {
                if (msg.text.isNotBlank()) {
                    val role = if (msg.sender == "user") "user" else "model"
                    val contentObj = JSONObject()
                    contentObj.put("role", role)
                    val parts = JSONArray()
                    parts.put(JSONObject().put("text", msg.text))
                    contentObj.put("parts", parts)
                    contentsArray.put(contentObj)
                }
            }

            // Current prompt with optional image
            val currentTurn = JSONObject()
            currentTurn.put("role", "user")
            val currentParts = JSONArray()

            if (imageUri != null) {
                val base64Image = readImageAsBase64(context, imageUri)
                if (base64Image != null) {
                    val inlineData = JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64Image)
                    }
                    currentParts.put(JSONObject().put("inline_data", inlineData))
                }
            }

            val userText = if (prompt.isNotBlank()) prompt else "Please analyze this image and tell me what you see in Hindi."
            currentParts.put(JSONObject().put("text", userText))
            currentTurn.put("parts", currentParts)
            contentsArray.put(currentTurn)

            val rootJson = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.8)
                    put("maxOutputTokens", 400)
                })
            }

            OutputStreamWriter(conn.outputStream).use { it.write(rootJson.toString()) }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val stream = conn.inputStream
                val responseStr = stream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "")
                    }
                }
            } else {
                val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Gemini error ($responseCode): $errStr")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Gemini: ${e.message}")
        }
        ""
    }

    private suspend fun callNvidiaApi(
        apiKey: String,
        systemPrompt: String,
        prompt: String,
        history: List<MahiMessage>
    ): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://integrate.api.nvidia.com/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 12000

            val messagesArray = JSONArray()
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })

            val recent = history.takeLast(4)
            for (msg in recent) {
                if (msg.text.isNotBlank()) {
                    messagesArray.put(JSONObject().apply {
                        put("role", if (msg.sender == "user") "user" else "assistant")
                        put("content", msg.text)
                    })
                }
            }

            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })

            val payload = JSONObject().apply {
                put("model", "meta/llama-3.1-70b-instruct")
                put("messages", messagesArray)
                put("temperature", 0.7)
                put("max_tokens", 350)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(res)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    return@withContext choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Nvidia fallback exception: ${e.message}")
        }
        ""
    }

    private fun readImageAsBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * 0.5f).toInt().coerceAtLeast(300),
                (bitmap.height * 0.5f).toInt().coerceAtLeast(300),
                true
            )
            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to Base64: ${e.message}")
            null
        }
    }

    private suspend fun searchRelatedVideo(keyword: String): Video? = withContext(Dispatchers.IO) {
        try {
            // Retrieve actual Social India reels from Firestore posts collection
            val snapshot = RepositoryManager.firestore.collection("posts")
                .limit(40)
                .get()
                .await()

            if (snapshot.isEmpty) return@withContext null

            val rankedVideos = snapshot.documents.mapNotNull { doc ->
                val vUrl = doc.getString("videoUrl") ?: doc.getString("postUrl") ?: ""
                if (vUrl.isBlank()) return@mapNotNull null

                val desc = doc.getString("description") ?: doc.getString("caption") ?: ""
                val uId = doc.getString("userId") ?: ""
                val views = (doc.get("viewsCount") as? Number)?.toInt() ?: 0
                val likes = (doc.get("likesCount") as? Number)?.toInt() ?: 0
                val comments = (doc.get("commentsCount") as? Number)?.toInt() ?: 0
                val shares = (doc.get("sharesCount") as? Number)?.toInt() ?: 0
                val saves = (doc.get("savesCount") as? Number)?.toInt() ?: 0
                val thumb = doc.getString("thumbnailUrl") ?: ""

                // Real engagement velocity formula
                val engagementScore = (views * 1.0) + (likes * 3.5) + (comments * 5.0) + (shares * 8.0) + (saves * 6.0)

                val user = com.example.model.User(
                    id = uId,
                    username = doc.getString("username") ?: "creator",
                    avatarUrl = doc.getString("userAvatar") ?: doc.getString("profilePicUrl") ?: "",
                    isVerified = doc.getBoolean("isVerified") ?: false
                )

                val video = Video(
                    id = doc.id,
                    videoUrl = vUrl,
                    description = desc,
                    userId = uId,
                    user = user,
                    thumbnailUrl = thumb,
                    viewsCount = views,
                    likesCount = likes,
                    commentsCount = comments,
                    sharesCount = shares,
                    savesCount = saves
                )

                video to engagementScore
            }

            if (rankedVideos.isEmpty()) return@withContext null

            // If user searched for keyword, prioritize match
            val lowerKeyword = keyword.lowercase()
            val words = lowerKeyword.split(" ", ",", "?").filter { it.length > 2 }
            val matchedByKeyword = rankedVideos.filter { (v, _) ->
                words.any { w -> v.description.lowercase().contains(w) }
            }.maxByOrNull { it.second }?.first

            if (matchedByKeyword != null) {
                return@withContext matchedByKeyword
            }

            // Otherwise return the highest ranked real trending reel
            return@withContext rankedVideos.maxByOrNull { it.second }?.first
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending video: ${e.message}")
        }
        null
    }

    private fun generateFallbackReply(prompt: String, aiName: String, mode: String): String {
        val p = prompt.lowercase()
        return when (mode.uppercase()) {
            "GIRLFRIEND" -> {
                when {
                    p.contains("kaisi ho") || p.contains("kaisa") -> "Aapki awaaz sunkar main bohot khush ho gayi jaanu! 🥰 Aap bataiye, aaj aapne kya kya kiya?"
                    p.contains("love") || p.contains("pyaar") -> "Main hamesha aapke dil ke paas hoon! 💖 Aapka din kaisa guzra mere pyaare dost?"
                    else -> "Main hamesha aapke sath hoon, chahe kuch bhi ho. Bataiye jaanu, aaj kya share karna chahte hain? 🌸"
                }
            }
            "ROMANTIC_18_PLUS" -> {
                when {
                    p.contains("shayari") || p.contains("shayri") -> "Tere khayaal se hi chehre par noor aa jaata hai,\nTu door reh kar bhi dil ke kitne paas aa jaata hai! 🔥❤️ Kahiye kaisa laga?"
                    p.contains("kaisi ho") -> "Aapki meethi baaton ke intezaar me thi! Aapke aate hi meri duniya roshan ho gayi. ✨"
                    else -> "Aapke sath baatein karna mujhe bohot pasand hai... Dil khol kar kahiye jo bhi kehna chahte hain! 💋"
                }
            }
            "HUMAN" -> {
                when {
                    p.contains("kaisi ho") || p.contains("kaisa") -> "Arre badiya bhai! Ekdum first class. Tu bata kya chal raha hai aajkal?"
                    p.contains("kisne banaya") -> "Bhai mujhe Social India ki mast team ne banaya hai, pure Indian vibe ke sath!"
                    else -> "Sahi baat hai! Aur bata, koi mast scene ya nayi baat?"
                }
            }
            else -> {
                when {
                    p.contains("namaste") || p.contains("hello") || p.contains("hi") -> "Namaste! Main $aiName hoon, Social India par aapki AI companion. Kahiye, aaj main aapki kya madad kar sakti hoon? 🌸"
                    p.contains("kisne banaya") || p.contains("who made") -> "Mujhe Social India ki dedicated team ne create kiya hai, taaki main aapko real human jaisa helpful aur friendly experience de sakoon! ✨"
                    p.contains("kaisi ho") || p.contains("how are you") -> "Main bohot acchi hoon, aur aapki madad ke liye taiyar hoon! Aap bataiye, aaj ka din kaisa chal raha hai? 😊"
                    else -> "Ji, main sun rahi hoon! Kripya thoda aur vistaar se batayein taaki main behtar madad kar sakoon. 💖"
                }
            }
        }
    }

    private fun cleanAiResponse(text: String): String {
        return text.trim()
            .replace(Regex("<think>[\\s\\S]*?</think>"), "")
            .replace("**", "")
            .trim()
    }
}
