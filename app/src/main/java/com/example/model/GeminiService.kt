package com.example.model

import com.example.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Common Data Classes ---

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<JsonObject>? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class ResponseFormat(
    val text: ResponseFormatText? = null
)

@Serializable
data class ResponseFormatText(
    val mimeType: String,
    val schema: JsonObject? = null
)

@Serializable
data class GenerationConfig(
    val responseFormat: ResponseFormat? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val responseModalities: List<String>? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: Content
)

// --- Retrofit Setup ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.8-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

object GeminiService {
    private val viralCaptionTemplates = listOf(
        "Vibe check passed 100% 🔥✨ Apna time aayega nahi, aa gaya hai! 🚀🇮🇳\n#SocialIndia #TrendingReels #ViralVibes #ExplorePage #DesiSwag #ReelsIndia",
        "Creating magic one frame at a time 🎬💫 Tag someone who needs to see this today! ❤️🙌\n#CreatorLife #ViralPost #InstaDaily #SocialIndia #ForYou #TrendingNow",
        "No shortcuts, just pure hustle & good vibes ⚡👑 Drop a 🔥 in the comments if you agree!\n#MotivationDaily #ReelKaroFeelKaro #ViralIndia #ExploreMore #TopCreator",
        "Moments that turn into forever memories ✨🌅 Which slide is your favorite? 1, 2 or 3? 👇\n#AestheticVibes #PhotoDump #SocialIndiaOfficial #Trending2026 #Viral"
    )

    suspend fun generateCaption(prompt: String): String = withContext(Dispatchers.IO) {
        val geminiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (geminiKey.isNotBlank()) {
            val request = GenerateContentRequest(
                contents = listOf(Content(
                    parts = listOf(Part(text = prompt))
                ))
            )
            try {
                val response = RetrofitClient.service.generateContent(geminiKey, request)
                val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!text.isNullOrBlank()) return@withContext text.trim()
            } catch (e: Exception) {
                android.util.Log.e("GeminiService", "Gemini error, trying NVIDIA fallback", e)
            }
        }

        // Fallback to NVIDIA NIM API
        val nvidiaKey = try { BuildConfig.NVIDIA_API_KEY } catch (e: Exception) { "" }
        if (nvidiaKey.isNotBlank()) {
            try {
                val url = java.net.URL("https://integrate.api.nvidia.com/v1/chat/completions")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $nvidiaKey")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doOutput = true

                val body = org.json.JSONObject()
                body.put("model", "meta/llama-3.2-11b-vision-instruct")
                val messages = org.json.JSONArray()
                val msg = org.json.JSONObject()
                msg.put("role", "user")
                msg.put("content", prompt)
                messages.put(msg)
                body.put("messages", messages)
                body.put("max_tokens", 256)

                java.io.OutputStreamWriter(conn.outputStream).use {
                    it.write(body.toString())
                    it.flush()
                }

                if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(resp)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val content = choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
                        if (content.isNotBlank()) return@withContext content.trim()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GeminiService", "Error in NVIDIA fallback", e)
            }
        }

        viralCaptionTemplates.random()
    }

    suspend fun translateText(text: String, targetLang: String = "Hindi"): String = withContext(Dispatchers.IO) {
        val geminiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (geminiKey.isNotBlank()) {
            try {
                val prompt = "Translate the following social media caption or comment naturally into $targetLang (keep emojis and hashtags intact, output ONLY the translated text): \"$text\""
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )
                val response = RetrofitClient.service.generateContent(geminiKey, request)
                val out = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                if (!out.isNullOrBlank()) return@withContext out
            } catch (_: Exception) {}
        }
        buildOfflineRegionalTranslation(text)
    }

    private fun buildOfflineRegionalTranslation(text: String): String {
        val dictionary = mapOf(
            "get ready" to "तैयार हो जाइए",
            "new season" to "नया सीज़न",
            "new goals" to "नए लक्ष्य",
            "new you" to "नया अंदाज़",
            "elevate your vibe" to "अपनी वाइब को और शानदार बनाएं",
            "good morning" to "सुप्रभात",
            "good night" to "शुभ रात्रि",
            "thank you" to "धन्यवाद",
            "love this" to "यह बहुत पसंद आया ❤️",
            "amazing" to "शानदार",
            "awesome" to "ज़बरदस्त",
            "friends" to "दोस्तों",
            "brother" to "भाई",
            "india" to "भारत 🇮🇳",
            "happy" to "खुश",
            "life" to "ज़िंदगी",
            "music" to "संगीत",
            "keep shining" to "चमकते रहो"
        )
        var translated = text
        dictionary.forEach { (eng, hin) ->
            translated = translated.replace(Regex(eng, RegexOption.IGNORE_CASE), hin)
        }
        return if (translated == text) {
            "🇮🇳 हिंदी अनुवाद: $text"
        } else {
            "🇮🇳 $translated"
        }
    }
}

