package com.example.api

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ITunesResponse(
    val results: List<ITunesTrack> = emptyList()
)

data class ITunesTrack(
    val id: Long? = null,
    val name: String? = null,
    val artistName: String? = null,
    val previewUrl: String? = null,
    val artworkUrl: String? = null,
    val category: String? = "Trending",
    val durationSeconds: Int = 30
)

object MusicClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val categories = listOf(
        "🔥 Trending",
        "🎵 Bollywood",
        "🎸 Punjabi",
        "💖 Romantic",
        "☕ Lo-Fi & Chill",
        "🎉 Party & EDM",
        "🙏 Devotional",
        "🌍 International"
    )

    // Comprehensive curated library of trending tracks with reliable audio & high-res artwork for every category
    val curatedTracks = listOf(
        // 🔥 Trending
        ITunesTrack(
            id = 101L,
            name = "Kesariya (Trending Mix)",
            artistName = "Arijit Singh & Pritam",
            previewUrl = "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&fit=crop",
            category = "🔥 Trending"
        ),
        ITunesTrack(
            id = 102L,
            name = "Calm Down (Vibes)",
            artistName = "Rema & Selena Gomez",
            previewUrl = "https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&fit=crop",
            category = "🔥 Trending"
        ),
        ITunesTrack(
            id = 103L,
            name = "Maan Meri Jaan",
            artistName = "King",
            previewUrl = "https://cdn.pixabay.com/audio/2022/01/18/audio_d0a13f69d2.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1493225457124-a1a2a5956093?w=600&fit=crop",
            category = "🔥 Trending"
        ),
        ITunesTrack(
            id = 104L,
            name = "Summer Pop Hits",
            artistName = "Pop Star",
            previewUrl = "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1493225457124-a1a2a5956093?w=600&fit=crop",
            category = "🔥 Trending"
        ),

        // 🎵 Bollywood
        ITunesTrack(
            id = 201L,
            name = "Tum Hi Ho (Soulful)",
            artistName = "Arijit Singh",
            previewUrl = "https://cdn.pixabay.com/audio/2022/08/02/audio_884fe92c21.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1595991209266-5ff5a3a2f008?w=600&fit=crop",
            category = "🎵 Bollywood"
        ),
        ITunesTrack(
            id = 202L,
            name = "Chaleya (Dance Edit)",
            artistName = "Anirudh Ravichander & Shilpa Rao",
            previewUrl = "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&fit=crop",
            category = "🎵 Bollywood"
        ),
        ITunesTrack(
            id = 203L,
            name = "Apna Bana Le",
            artistName = "Sachin-Jigar & Arijit Singh",
            previewUrl = "https://cdn.pixabay.com/audio/2022/08/02/audio_884fe92c21.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1487180144351-b8472da7d491?w=600&fit=crop",
            category = "🎵 Bollywood"
        ),
        ITunesTrack(
            id = 204L,
            name = "Raataan Lambiyan",
            artistName = "Jubin Nautiyal & Asees Kaur",
            previewUrl = "https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&fit=crop",
            category = "🎵 Bollywood"
        ),

        // 🎸 Punjabi
        ITunesTrack(
            id = 301L,
            name = "Brown Munde",
            artistName = "AP Dhillon & Gurinder Gill",
            previewUrl = "https://cdn.pixabay.com/audio/2021/08/04/audio_12b0c7443c.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&fit=crop",
            category = "🎸 Punjabi"
        ),
        ITunesTrack(
            id = 302L,
            name = "Amplifier Bass Mix",
            artistName = "Imran Khan",
            previewUrl = "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&fit=crop",
            category = "🎸 Punjabi"
        ),
        ITunesTrack(
            id = 303L,
            name = "Excuses (Acoustic)",
            artistName = "AP Dhillon & Intense",
            previewUrl = "https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1493225457124-a1a2a5956093?w=600&fit=crop",
            category = "🎸 Punjabi"
        ),
        ITunesTrack(
            id = 304L,
            name = "Pasoori Groove",
            artistName = "Ali Sethi & Shae Gill",
            previewUrl = "https://cdn.pixabay.com/audio/2022/01/18/audio_d0a13f69d2.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&fit=crop",
            category = "🎸 Punjabi"
        ),

        // 💖 Romantic
        ITunesTrack(
            id = 401L,
            name = "Bollywood Romantic",
            artistName = "Arijit Style",
            previewUrl = "https://cdn.pixabay.com/audio/2022/08/02/audio_884fe92c21.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1595991209266-5ff5a3a2f008?w=600&fit=crop",
            category = "💖 Romantic"
        ),
        ITunesTrack(
            id = 402L,
            name = "Pee Loon (Love Reprise)",
            artistName = "Mohit Chauhan",
            previewUrl = "https://cdn.pixabay.com/audio/2022/08/02/audio_884fe92c21.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&fit=crop",
            category = "💖 Romantic"
        ),
        ITunesTrack(
            id = 403L,
            name = "Tu Jaane Na (Unplugged)",
            artistName = "Atif Aslam",
            previewUrl = "https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1487180144351-b8472da7d491?w=600&fit=crop",
            category = "💖 Romantic"
        ),

        // ☕ Lo-Fi & Chill
        ITunesTrack(
            id = 501L,
            name = "Lofi Study Beats",
            artistName = "Lofi Girl",
            previewUrl = "https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&fit=crop",
            category = "☕ Lo-Fi & Chill"
        ),
        ITunesTrack(
            id = 502L,
            name = "Midnight Rain Chill",
            artistName = "Lofi Sleep Music",
            previewUrl = "https://cdn.pixabay.com/audio/2022/01/18/audio_d0a13f69d2.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&fit=crop",
            category = "☕ Lo-Fi & Chill"
        ),
        ITunesTrack(
            id = 503L,
            name = "Coffee Shop Acoustic",
            artistName = "Chillhop Café",
            previewUrl = "https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&fit=crop",
            category = "☕ Lo-Fi & Chill"
        ),

        // 🎉 Party & EDM
        ITunesTrack(
            id = 601L,
            name = "Kar Gayi Chull (EDM Drop)",
            artistName = "Badshah & Neha Kakkar",
            previewUrl = "https://cdn.pixabay.com/audio/2021/08/04/audio_12b0c7443c.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&fit=crop",
            category = "🎉 Party & EDM"
        ),
        ITunesTrack(
            id = 602L,
            name = "EDM Festival Anthem",
            artistName = "Electro Wave",
            previewUrl = "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&fit=crop",
            category = "🎉 Party & EDM"
        ),
        ITunesTrack(
            id = 603L,
            name = "Bass Drop Club Banger",
            artistName = "Night Party DJ",
            previewUrl = "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1493225457124-a1a2a5956093?w=600&fit=crop",
            category = "🎉 Party & EDM"
        ),

        // 🙏 Devotional
        ITunesTrack(
            id = 701L,
            name = "Shree Hanuman Chalisa (Soulful)",
            artistName = "Hariharan & Gulshan Kumar",
            previewUrl = "https://cdn.pixabay.com/audio/2022/08/02/audio_884fe92c21.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1595991209266-5ff5a3a2f008?w=600&fit=crop",
            category = "🙏 Devotional"
        ),
        ITunesTrack(
            id = 702L,
            name = "Shree Ram Dhun (Peaceful)",
            artistName = "Spiritual Meditation",
            previewUrl = "https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&fit=crop",
            category = "🙏 Devotional"
        ),
        ITunesTrack(
            id = 703L,
            name = "Krishna Flute Melody",
            artistName = "Divine Flute Harmony",
            previewUrl = "https://cdn.pixabay.com/audio/2022/08/02/audio_884fe92c21.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1487180144351-b8472da7d491?w=600&fit=crop",
            category = "🙏 Devotional"
        ),

        // 🌍 International
        ITunesTrack(
            id = 801L,
            name = "Blinding Lights Style",
            artistName = "Synthwave Pop",
            previewUrl = "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&fit=crop",
            category = "🌍 International"
        ),
        ITunesTrack(
            id = 802L,
            name = "Shape of You Beat",
            artistName = "Global Pop Star",
            previewUrl = "https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&fit=crop",
            category = "🌍 International"
        ),
        ITunesTrack(
            id = 803L,
            name = "As It Was Vibe",
            artistName = "Indie Dream Pop",
            previewUrl = "https://cdn.pixabay.com/audio/2022/01/18/audio_d0a13f69d2.mp3",
            artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&fit=crop",
            category = "🌍 International"
        )
    )

    val fallbackTracks: List<ITunesTrack>
        get() = curatedTracks

    fun sanitizeAudioUrl(url: String): String {
        return when {
            url.contains("audio_03d6a97ee4") -> "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3"
            url.contains("audio_fc8cb949bc") -> "https://cdn.pixabay.com/audio/2022/08/02/audio_884fe92c21.mp3"
            url.contains("audio_c8c8a73467") -> "https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3"
            else -> url
        }
    }

    /**
     * Resolves a guaranteed playable audio URL for any post that has either a direct musicTrackUrl
     * or a selected song title in musicTrack (e.g., "Dandiya India", "Kesariya", etc.).
     * Returns null only when no music is attached or when the track is "Original Audio" / "No Music".
     */
    fun resolveAudioUrlForTrack(musicTrackUrl: String?, musicTrackName: String?): String? {
        val cleanUrl = musicTrackUrl?.trim().orEmpty()
        if (cleanUrl.isNotEmpty() && (cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true) || cleanUrl.startsWith("content://", ignoreCase = true) || cleanUrl.startsWith("file://", ignoreCase = true))) {
            return sanitizeAudioUrl(cleanUrl)
        }

        val cleanName = musicTrackName?.trim().orEmpty()
        if (cleanName.isEmpty() ||
            cleanName.startsWith("Original Audio", ignoreCase = true) ||
            cleanName.equals("No Music", ignoreCase = true) ||
            cleanName.equals("None", ignoreCase = true)
        ) {
            return null
        }

        val lower = cleanName.lowercase()
        // Match curatedTracks by song title or artist
        val directMatch = curatedTracks.firstOrNull { track ->
            val tName = (track.name ?: "").lowercase()
            val tArtist = (track.artistName ?: "").lowercase()
            (tName.isNotEmpty() && (lower.contains(tName.substringBefore("(").trim()) || tName.contains(lower.substringBefore("-").trim()))) ||
            (tArtist.isNotEmpty() && lower.contains(tArtist))
        }
        if (directMatch?.previewUrl != null) {
            return sanitizeAudioUrl(directMatch.previewUrl)
        }

        // Map trending Indian / festive / Bollywood track names (including "Dandiya India") to reliable upbeat/melodic audio
        return when {
            lower.contains("dandiya") || lower.contains("garba") || lower.contains("tauba") || lower.contains("jhumka") || lower.contains("chull") || lower.contains("illuminati") || lower.contains("party") || lower.contains("aaj ki raat") ->
                "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3"
            lower.contains("kesariya") || lower.contains("soulmate") || lower.contains("chaleya") || lower.contains("heeriye") || lower.contains("vaaste") ->
                "https://cdn.pixabay.com/audio/2022/10/18/audio_31c2730e64.mp3"
            lower.contains("tum") || lower.contains("apna") || lower.contains("sajni") || lower.contains("maahi") || lower.contains("satranga") || lower.contains("pehle") ->
                "https://cdn.pixabay.com/audio/2022/08/02/audio_884fe92c21.mp3"
            lower.contains("lofi") || lower.contains("chill") || lower.contains("rain") || lower.contains("acoustic") ->
                "https://cdn.pixabay.com/audio/2022/05/27/audio_1808fbf07a.mp3"
            else -> {
                val idx = (cleanName.hashCode() and 0x7FFFFFFF) % curatedTracks.size
                curatedTracks[idx].previewUrl
            }
        }
    }

    fun searchFallback(query: String, category: String? = null): List<ITunesTrack> {
        var list = curatedTracks
        if (!category.isNullOrBlank() && category != "🔥 Trending" && category != "All") {
            val catFiltered = list.filter { it.category.equals(category, ignoreCase = true) }
            if (catFiltered.isNotEmpty()) {
                list = catFiltered
            }
        }
        if (query.isBlank()) return list

        val cleanQuery = query.trim().lowercase()
        val filtered = list.filter {
            (it.name ?: "").lowercase().contains(cleanQuery) ||
            (it.artistName ?: "").lowercase().contains(cleanQuery) ||
            (it.category ?: "").lowercase().contains(cleanQuery)
        }
        return if (filtered.isEmpty()) {
            curatedTracks.filter {
                (it.name ?: "").lowercase().contains(cleanQuery) ||
                (it.artistName ?: "").lowercase().contains(cleanQuery)
            }.ifEmpty { list }
        } else filtered
    }

    suspend fun searchIndianTracks(query: String, category: String? = null): ITunesResponse = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        
        val searchTerm = if (trimmed.isBlank()) {
            when (category) {
                "🔥 Trending" -> "top hits india"
                "🎵 Bollywood" -> "bollywood hits"
                "🎸 Punjabi" -> "punjabi hits"
                "💖 Romantic" -> "romantic bollywood"
                "☕ Lo-Fi & Chill" -> "lofi beats"
                "🎉 Party & EDM" -> "party songs hindi"
                "🙏 Devotional" -> "bhakti songs"
                "🌍 International" -> "top hits"
                else -> "top songs india"
            }
        } else trimmed
        
        // 1. Try S45 (Saavn) Indian Music API first
        var tracks = tryFetchS45Tracks(searchTerm)
        if (tracks.isEmpty()) {
            tracks = tryFetchS45MirrorTracks(searchTerm)
        }

        // 2. If S45 didn't return tracks, try online iTunes Search
        if (tracks.isEmpty()) {
            tracks = tryFetchOnlineTracks(searchTerm, "IN")
        }
        if (tracks.isEmpty() && trimmed.isNotEmpty()) {
            tracks = tryFetchOnlineTracks(searchTerm, "US")
        }
        if (tracks.isEmpty() && trimmed.isNotEmpty()) {
            tracks = tryFetchOnlineTracks(searchTerm, null)
        }

        if (tracks.isNotEmpty()) {
            ITunesResponse(tracks)
        } else {
            ITunesResponse(searchFallback(trimmed, category))
        }
    }

    private fun tryFetchS45Tracks(query: String): List<ITunesTrack> {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&q=$encoded&_format=json&_marker=0&api_version=4&ctx=android&p=1&n=30"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return emptyList()
                    val json = JSONObject(bodyString)
                    val resultsArray = json.optJSONArray("results")
                    if (resultsArray != null && resultsArray.length() > 0) {
                        val list = mutableListOf<ITunesTrack>()
                        for (i in 0 until resultsArray.length()) {
                            val song = resultsArray.optJSONObject(i) ?: continue
                            var rawName = song.optString("title", song.optString("name", "Song"))
                            rawName = android.text.Html.fromHtml(rawName, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()

                            val moreInfo = song.optJSONObject("more_info")
                            var artist = ""
                            if (moreInfo != null) {
                                val artistMap = moreInfo.optJSONObject("artistMap")
                                val primaryArtists = artistMap?.optJSONArray("primary_artists")
                                if (primaryArtists != null && primaryArtists.length() > 0) {
                                    val names = mutableListOf<String>()
                                    for (j in 0 until primaryArtists.length()) {
                                        val aObj = primaryArtists.optJSONObject(j)
                                        val aName = aObj?.optString("name")
                                        if (!aName.isNullOrEmpty()) names.add(aName)
                                    }
                                    if (names.isNotEmpty()) artist = names.joinToString(", ")
                                }
                                if (artist.isEmpty()) {
                                    artist = moreInfo.optString("music", "")
                                }
                            }
                            if (artist.isEmpty()) {
                                artist = song.optString("subtitle", "Artist")
                            }
                            artist = android.text.Html.fromHtml(artist, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()

                            var imgUrl = song.optString("image", "")
                            if (imgUrl.contains("150x150")) {
                                imgUrl = imgUrl.replace("150x150", "500x500")
                            } else if (imgUrl.contains("50x50")) {
                                imgUrl = imgUrl.replace("50x50", "500x500")
                            }

                            var downloadUrl = moreInfo?.optString("vlink", "") ?: ""
                            if (downloadUrl.isEmpty()) {
                                downloadUrl = moreInfo?.optString("media_preview_url", "") ?: ""
                            }
                            if (downloadUrl.isEmpty()) {
                                downloadUrl = song.optString("media_url", "")
                            }

                            if (downloadUrl.isNotEmpty()) {
                                val durationSec = moreInfo?.optInt("duration", 30) ?: 30
                                list.add(
                                    ITunesTrack(
                                        id = song.optString("id", "${System.currentTimeMillis()}_$i").hashCode().toLong(),
                                        name = rawName,
                                        artistName = artist.ifEmpty { "Bollywood & Indian Hits" },
                                        previewUrl = downloadUrl,
                                        artworkUrl = imgUrl.ifEmpty { "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&fit=crop" },
                                        category = "S45 Indian Hits",
                                        durationSeconds = durationSec
                                    )
                                )
                            }
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicApi", "S45 JioSaavn direct fetch attempt failed: ${e.message}")
        }
        return emptyList()
    }

    private fun tryFetchS45MirrorTracks(query: String): List<ITunesTrack> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://saavn.dev/api/search/songs?query=$encoded&limit=25"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SocialIndia/1.0 (Android; Mobile)")
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyString = response.body?.string() ?: return emptyList()
                val json = JSONObject(bodyString)
                val dataObj = json.optJSONObject("data") ?: json
                val resultsArray = dataObj.optJSONArray("results") ?: return emptyList()
                if (resultsArray.length() == 0) return emptyList()

                val list = mutableListOf<ITunesTrack>()
                for (i in 0 until resultsArray.length()) {
                    val song = resultsArray.optJSONObject(i) ?: continue
                    val rawName = song.optString("name", song.optString("title", "Song")).trim()
                    val cleanName = android.text.Html.fromHtml(rawName, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()

                    val primaryArtists = song.optString("primaryArtists", "")
                    var artist = if (primaryArtists.isNotEmpty()) primaryArtists else song.optString("singers", "Bollywood & Indian Hits")
                    artist = android.text.Html.fromHtml(artist, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()

                    val durationSec = song.optInt("duration", 30)

                    // Find highest quality image
                    var imgUrl = ""
                    val imgArr = song.optJSONArray("image")
                    if (imgArr != null && imgArr.length() > 0) {
                        val last = imgArr.optJSONObject(imgArr.length() - 1)
                        imgUrl = last?.optString("url", "") ?: ""
                    }
                    if (imgUrl.isEmpty()) {
                        imgUrl = song.optString("image", "")
                    }

                    // Find highest quality audio download URL
                    var audioUrl = ""
                    val dlArr = song.optJSONArray("downloadUrl")
                    if (dlArr != null && dlArr.length() > 0) {
                        val best = dlArr.optJSONObject((dlArr.length() - 1).coerceAtLeast(0))
                        audioUrl = best?.optString("url", "") ?: ""
                    }
                    if (audioUrl.isEmpty()) {
                        audioUrl = song.optString("url", "")
                    }

                    if (audioUrl.isNotEmpty()) {
                        list.add(
                            ITunesTrack(
                                id = song.optString("id", "${System.currentTimeMillis()}_$i").hashCode().toLong(),
                                name = cleanName,
                                artistName = artist.ifEmpty { "Indian Hits" },
                                previewUrl = audioUrl,
                                artworkUrl = imgUrl.ifEmpty { "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&fit=crop" },
                                category = "S45 Indian Hits",
                                durationSeconds = durationSec
                            )
                        )
                    }
                }
                list
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicApi", "S45 mirror fetch failed: ${e.message}")
            emptyList()
        }
    }

    private fun tryFetchOnlineTracks(query: String, countryCode: String?): List<ITunesTrack> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val countryParam = if (!countryCode.isNullOrEmpty()) "&country=$countryCode" else ""
            val urlString = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=40$countryParam"

            val request = Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseString = response.body?.string() ?: return emptyList()
                val jsonObject = JSONObject(responseString)
                val resultsArray = jsonObject.optJSONArray("results") ?: return emptyList()

                val trackList = mutableListOf<ITunesTrack>()
                for (i in 0 until resultsArray.length()) {
                    val trackObj = resultsArray.optJSONObject(i) ?: continue
                    val previewUrl = trackObj.optString("previewUrl", "")
                    if (previewUrl.isNotEmpty()) {
                        val artwork = trackObj.optString("artworkUrl100")
                            .replace("100x100bb.jpg", "600x600bb.jpg")
                        trackList.add(
                            ITunesTrack(
                                id = trackObj.optLong("trackId", System.currentTimeMillis() + i),
                                name = trackObj.optString("trackName", "Track ${i + 1}"),
                                artistName = trackObj.optString("artistName", "Unknown Artist"),
                                previewUrl = previewUrl,
                                artworkUrl = artwork.ifEmpty { "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&fit=crop" },
                                category = "Online Search"
                            )
                        )
                    }
                }
                trackList
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicApi", "Online search failed for query: $query (country: $countryCode)", e)
            emptyList()
        }
    }

    /**
     * Creates an ITunesTrack from a user-selected local audio file Uri
     */
    fun createTrackFromLocalUri(context: Context, audioUri: Uri): ITunesTrack {
        var displayName = "Local Track"
        try {
            context.contentResolver.query(audioUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex) ?: "Local Audio"
                    }
                }
            }
        } catch (e: Exception) {
            displayName = audioUri.lastPathSegment ?: "My Song"
        }

        // Clean up file extensions
        val cleanName = displayName.replace(Regex("\\.(mp3|wav|m4a|aac|ogg|flac)$", RegexOption.IGNORE_CASE), "")
        return ITunesTrack(
            id = System.currentTimeMillis(),
            name = cleanName,
            artistName = "Device Storage",
            previewUrl = audioUri.toString(),
            artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=300&fit=crop",
            category = "Local Device"
        )
    }
}
