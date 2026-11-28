package com.example.zego

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Social India Token Service for ZEGO authentication.
 * Manages short-lived token fetching and secure authentication fallback.
 */
object SocialIndiaZegoTokenService : ZegoTokenProvider {
    private const val TAG = "ZegoTokenService"

    override suspend fun getToken(roomId: String, userId: String): String? = withContext(Dispatchers.IO) {
        try {
            // For AppSign based initialization (the standard ZEGO Android UIKit mode),
            // AppSign is passed during fragment creation and tokens are optional.
            // If backend token endpoint is configured, fetch here.
            null
        } catch (e: Exception) {
            Log.e(TAG, "Token fetch failed", e)
            null
        }
    }
}
