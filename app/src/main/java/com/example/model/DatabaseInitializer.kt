package com.example.model

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

object DatabaseInitializer {
    suspend fun initializeUserSchemaSafe(userId: String) {
        if (userId.isEmpty()) return
        try {
            val firestore = RepositoryManager.firestore
            val docRef = firestore.collection("users").document(userId)
            val doc = docRef.get().await()
            
            val updateData = mutableMapOf<String, Any>()
            
            if (!doc.exists()) {
                val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val initialName = currentAuthUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: currentAuthUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "User"
                updateData["uid"] = userId
                updateData["username"] = initialName
                updateData["normalizedUsername"] = initialName.lowercase()
                updateData["profilePicUrl"] = currentAuthUser?.photoUrl?.toString() ?: ""
                updateData["createdAt"] = FieldValue.serverTimestamp()
            } else {
                val currentUsername = doc.getString("username")
                val currentNormalized = doc.getString("normalizedUsername")
                if (currentUsername != null && (currentNormalized == null || currentNormalized != currentUsername.lowercase())) {
                    updateData["normalizedUsername"] = currentUsername.lowercase()
                }
                
                val currentFullName = doc.getString("fullName") ?: doc.getString("name")
                if (currentFullName != null && !doc.contains("normalizedFullName")) {
                    updateData["normalizedFullName"] = currentFullName.lowercase()
                }
            }
            
            val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val authEmail = authUser?.email.orEmpty().trim()
            val userEmail = doc.getString("email").orEmpty().trim()
            val isMasterAdmin = authEmail.equals("abhinandankumar7867@gmail.com", ignoreCase = true) ||
                                userEmail.equals("abhinandankumar7867@gmail.com", ignoreCase = true)

            val safeDefaults = mutableMapOf<String, Any>(
                "bio" to "",
                "profilePicUrl" to "",
                "followerCount" to 0,
                "followingCount" to 0,
                "postsCount" to 0,
                "reelsCount" to 0,
                "isVerified" to false,
                "accountType" to "PERSONAL",
                "isProfessionalAccount" to false,
                "walletBalance" to 0.0,
                "isBanned" to false,
                "securityLevel" to "HIGH",
                "accountStatus" to "ACTIVE"
            )

            if (isMasterAdmin) {
                updateData["isAdmin"] = true
                updateData["isVerified"] = true
                updateData["verificationStatus"] = "approved"
                updateData["verificationBadgeUrl"] = "https://i.ibb.co/5g7pLKGW/checklist.png"
            }
            
            for ((key, value) in safeDefaults) {
                if (!doc.contains(key)) {
                    updateData[key] = value
                }
            }
            
            if (updateData.isNotEmpty()) {
                docRef.set(updateData, SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            android.util.Log.e("DatabaseInitializer", "Failed to initialize user schema", e)
        }
    }
}
