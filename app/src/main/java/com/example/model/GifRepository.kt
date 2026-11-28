package com.example.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class GifItem(
    val id: String,
    val title: String,
    val previewUrl: String,
    val fullUrl: String,
    val category: String = "trending"
)

object GifRepository {
    private const val TAG = "GifRepository"
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val searchCache = mutableMapOf<String, List<GifItem>>()

    // Curated rich animated GIFs for immediate, zero-lag loading across popular categories
    val CURATED_GIFS = listOf(
        // Trending / Reactions
        GifItem("t1", "Mind Blown", "https://media.giphy.com/media/26ufdipQqU2lhNA4g/giphy.gif", "https://media.giphy.com/media/26ufdipQqU2lhNA4g/giphy.gif", "trending"),
        GifItem("t2", "Thumbs Up Cool", "https://media.giphy.com/media/111ebonMs90YLu/giphy.gif", "https://media.giphy.com/media/111ebonMs90YLu/giphy.gif", "trending"),
        GifItem("t3", "Celebration Dance", "https://media.giphy.com/media/blSTtZehjAZ8I/giphy.gif", "https://media.giphy.com/media/blSTtZehjAZ8I/giphy.gif", "trending"),
        GifItem("t4", "Fire Lit", "https://media.giphy.com/media/nrXif9YExO9EI/giphy.gif", "https://media.giphy.com/media/nrXif9YExO9EI/giphy.gif", "trending"),
        GifItem("t5", "Popcorn Watching", "https://media.giphy.com/media/gl0mkIZOW6Nwc/giphy.gif", "https://media.giphy.com/media/gl0mkIZOW6Nwc/giphy.gif", "trending"),
        GifItem("t6", "Omg Wow", "https://media.giphy.com/media/l3q2K5jinAlChoCLS/giphy.gif", "https://media.giphy.com/media/l3q2K5jinAlChoCLS/giphy.gif", "trending"),

        // Bollywood / Desi
        GifItem("b1", "SRK Signature Pose", "https://media.giphy.com/media/3ofT5NpqKOYq1YKDxe/giphy.gif", "https://media.giphy.com/media/3ofT5NpqKOYq1YKDxe/giphy.gif", "bollywood"),
        GifItem("b2", "Salman Swag Dance", "https://media.giphy.com/media/3otPoxYgX0Y5WK7hgQ/giphy.gif", "https://media.giphy.com/media/3otPoxYgX0Y5WK7hgQ/giphy.gif", "bollywood"),
        GifItem("b3", "Ranveer High Energy", "https://media.giphy.com/media/26n61nGDs97VzpQUo/giphy.gif", "https://media.giphy.com/media/26n61nGDs97VzpQUo/giphy.gif", "bollywood"),
        GifItem("b4", "Deepika Smile", "https://media.giphy.com/media/3ohfFrepDFb0MP8ppu/giphy.gif", "https://media.giphy.com/media/3ohfFrepDFb0MP8ppu/giphy.gif", "bollywood"),
        GifItem("b5", "Kareena Poo Attitude", "https://media.giphy.com/media/l1IY72n949rr0zbSU/giphy.gif", "https://media.giphy.com/media/l1IY72n949rr0zbSU/giphy.gif", "bollywood"),
        GifItem("b6", "Amitabh Bachchan Shahenshah", "https://media.giphy.com/media/3o6wrvpt1s8IZGx5W8/giphy.gif", "https://media.giphy.com/media/3o6wrvpt1s8IZGx5W8/giphy.gif", "bollywood"),

        // Funny / Memes
        GifItem("m1", "Laughing Crying LOL", "https://media.giphy.com/media/10JhviFuU2gWD6/giphy.gif", "https://media.giphy.com/media/10JhviFuU2gWD6/giphy.gif", "funny"),
        GifItem("m2", "Cat Vibing Meme", "https://media.giphy.com/media/jpbnoe3UIa8TU8LM13/giphy.gif", "https://media.giphy.com/media/jpbnoe3UIa8TU8LM13/giphy.gif", "funny"),
        GifItem("m3", "Drake Hotline Bling", "https://media.giphy.com/media/3o7527pa7qs9kCG78A/giphy.gif", "https://media.giphy.com/media/3o7527pa7qs9kCG78A/giphy.gif", "funny"),
        GifItem("m4", "Confused Math Lady", "https://media.giphy.com/media/3o7btPCcdNniyf0ArS/giphy.gif", "https://media.giphy.com/media/3o7btPCcdNniyf0ArS/giphy.gif", "funny"),
        GifItem("m5", "Dog Side Eye", "https://media.giphy.com/media/H5C8CevNMbpBqNqFjl/giphy.gif", "https://media.giphy.com/media/H5C8CevNMbpBqNqFjl/giphy.gif", "funny"),
        GifItem("m6", "Khabe Lame Simple", "https://media.giphy.com/media/z8rNp3MpBCWec/giphy.gif", "https://media.giphy.com/media/z8rNp3MpBCWec/giphy.gif", "funny"),

        // Love & Cute
        GifItem("l1", "Heart Explosion Love", "https://media.giphy.com/media/l4pTdcifPZLpDjL1e/giphy.gif", "https://media.giphy.com/media/l4pTdcifPZLpDjL1e/giphy.gif", "love"),
        GifItem("l2", "Flying Kisses", "https://media.giphy.com/media/3o7TKoWXm3okO1kgHC/giphy.gif", "https://media.giphy.com/media/3o7TKoWXm3okO1kgHC/giphy.gif", "love"),
        GifItem("l3", "Cute Hug Bear", "https://media.giphy.com/media/MDJ9IbxxvDUQM/giphy.gif", "https://media.giphy.com/media/MDJ9IbxxvDUQM/giphy.gif", "love"),
        GifItem("l4", "Puppy Eyes Cute", "https://media.giphy.com/media/ICOgUNjpvO0PC/giphy.gif", "https://media.giphy.com/media/ICOgUNjpvO0PC/giphy.gif", "love"),
        GifItem("l5", "Romantic Roses", "https://media.giphy.com/media/26FLdm964upIslUZ2/giphy.gif", "https://media.giphy.com/media/26FLdm964upIslUZ2/giphy.gif", "love"),

        // Cricket / Sports
        GifItem("c1", "Virat Kohli Century Celebration", "https://media.giphy.com/media/XG0vb5dJwaCeI/giphy.gif", "https://media.giphy.com/media/XG0vb5dJwaCeI/giphy.gif", "cricket"),
        GifItem("c2", "MS Dhoni Helicopter Shot", "https://media.giphy.com/media/3ohfFhG5VDjiOBNHWe/giphy.gif", "https://media.giphy.com/media/3ohfFhG5VDjiOBNHWe/giphy.gif", "cricket"),
        GifItem("c3", "Rohit Sharma Six", "https://media.giphy.com/media/l4KhQo2MESJkc6HyM/giphy.gif", "https://media.giphy.com/media/l4KhQo2MESJkc6HyM/giphy.gif", "cricket"),
        GifItem("c4", "Trophy Victory Cheering", "https://media.giphy.com/media/3o7abKhOpu0NwenH3O/giphy.gif", "https://media.giphy.com/media/3o7abKhOpu0NwenH3O/giphy.gif", "cricket"),

        // Dance & Party
        GifItem("d1", "Party Disco Dancing", "https://media.giphy.com/media/DhstvI455Y0sE/giphy.gif", "https://media.giphy.com/media/DhstvI455Y0sE/giphy.gif", "dance"),
        GifItem("d2", "Breakdance Moves", "https://media.giphy.com/media/mKMGLhoD8L4yc/giphy.gif", "https://media.giphy.com/media/mKMGLhoD8L4yc/giphy.gif", "dance"),
        GifItem("d3", "Bhangra Dhol Energy", "https://media.giphy.com/media/l2YWvI3Zl4uCqB46c/giphy.gif", "https://media.giphy.com/media/l2YWvI3Zl4uCqB46c/giphy.gif", "dance"),
        GifItem("d4", "Groovy Cool Vibes", "https://media.giphy.com/media/l3vRlT2k2L35Cnn5C/giphy.gif", "https://media.giphy.com/media/l3vRlT2k2L35Cnn5C/giphy.gif", "dance")
    )

    suspend fun getTrendingGifs(limit: Int = 30): List<GifItem> = withContext(Dispatchers.IO) {
        // Try live GIPHY trending API
        val liveGifs = fetchFromGiphyTrending(limit)
        if (liveGifs.isNotEmpty()) {
            return@withContext liveGifs
        }
        CURATED_GIFS
    }

    suspend fun searchGifs(query: String, limit: Int = 30): List<GifItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return@withContext CURATED_GIFS

        searchCache[trimmed]?.let { return@withContext it }

        // Try live API search first
        val apiResults = fetchFromGiphySearch(trimmed, limit)
        if (apiResults.isNotEmpty()) {
            searchCache[trimmed] = apiResults
            return@withContext apiResults
        }

        // Fallback to local curated search matching keywords
        val localFiltered = CURATED_GIFS.filter {
            it.title.lowercase().contains(trimmed) ||
            it.category.lowercase().contains(trimmed)
        }
        if (localFiltered.isNotEmpty()) {
            return@withContext localFiltered
        }

        // Return empty if no matches
        emptyList()
    }

    suspend fun getGifsByCategory(category: String): List<GifItem> = withContext(Dispatchers.IO) {
        val key = category.lowercase().trim()
        if (key == "trending" || key == "all") {
            return@withContext getTrendingGifs()
        }
        val byCat = CURATED_GIFS.filter { it.category.equals(key, ignoreCase = true) }
        if (byCat.isNotEmpty()) {
            val live = searchGifs(category, 20)
            return@withContext (byCat + live).distinctBy { it.id }
        }
        searchGifs(category, 20)
    }

    private fun fetchFromGiphyTrending(limit: Int): List<GifItem> {
        val apiKeys = listOf(
            "sXpGFDGZs0Dv1mmNFvYaGUvYwKX0P4W9",
            "0UTRbPtkM2vCTKX137WbxUCgahSMmWC3",
            "dc6zaTOxFJmzC"
        )
        for (key in apiKeys) {
            try {
                val url = "https://api.giphy.com/v1/gifs/trending?api_key=$key&limit=$limit&rating=g"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val data = json.optJSONArray("data") ?: return@use
                        val list = mutableListOf<GifItem>()
                        for (i in 0 until data.length()) {
                            val item = data.getJSONObject(i)
                            val id = item.optString("id")
                            val title = item.optString("title").ifEmpty { "GIF" }
                            val images = item.optJSONObject("images")
                            val fixedHeight = images?.optJSONObject("fixed_height")
                            val original = images?.optJSONObject("original")
                            val previewUrl = fixedHeight?.optString("url") ?: original?.optString("url") ?: ""
                            val fullUrl = original?.optString("url") ?: previewUrl
                            if (fullUrl.isNotEmpty()) {
                                list.add(GifItem(id, title, previewUrl, fullUrl, "trending"))
                            }
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Giphy trending error: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun fetchFromGiphySearch(query: String, limit: Int): List<GifItem> {
        val encoded = try { URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val apiKeys = listOf(
            "sXpGFDGZs0Dv1mmNFvYaGUvYwKX0P4W9",
            "0UTRbPtkM2vCTKX137WbxUCgahSMmWC3",
            "dc6zaTOxFJmzC"
        )
        for (key in apiKeys) {
            try {
                val url = "https://api.giphy.com/v1/gifs/search?api_key=$key&q=$encoded&limit=$limit&rating=g"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val data = json.optJSONArray("data") ?: return@use
                        val list = mutableListOf<GifItem>()
                        for (i in 0 until data.length()) {
                            val item = data.getJSONObject(i)
                            val id = item.optString("id")
                            val title = item.optString("title").ifEmpty { query }
                            val images = item.optJSONObject("images")
                            val fixedHeight = images?.optJSONObject("fixed_height")
                            val original = images?.optJSONObject("original")
                            val previewUrl = fixedHeight?.optString("url") ?: original?.optString("url") ?: ""
                            val fullUrl = original?.optString("url") ?: previewUrl
                            if (fullUrl.isNotEmpty()) {
                                list.add(GifItem(id, title, previewUrl, fullUrl, query))
                            }
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Giphy search error for $query: ${e.message}")
            }
        }
        return emptyList()
    }
}
