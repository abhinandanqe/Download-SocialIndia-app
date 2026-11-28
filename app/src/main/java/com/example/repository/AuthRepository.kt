package com.example.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    suspend fun signUp(email: String, pass: String, username: String) {
        val result = auth.createUserWithEmailAndPassword(email, pass).await()
        val uid = result.user?.uid ?: throw IllegalStateException("Account created but user ID is unavailable")
        
        val user = hashMapOf(
            "uid" to uid,
            "username" to username,
            "profilePicUrl" to "",
            "bio" to ""
        )
        
        try {
            firestore.collection("users").document(uid).set(user).await()
            com.example.model.DatabaseInitializer.initializeUserSchemaSafe(uid)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Failed to save user profile due to rules", e)
            // DO NOT THROW, Just ignore it so login works!
        }
    }

    suspend fun isEmailRegistered(email: String): Boolean {
        val trimmed = email.trim().lowercase()
        if (trimmed.isEmpty()) return false
        return try {
            val result = auth.fetchSignInMethodsForEmail(trimmed).await()
            val methods = result.signInMethods
            methods != null && methods.isNotEmpty()
        } catch (e: Exception) {
            android.util.Log.w("AuthRepository", "fetchSignInMethodsForEmail check failed: ${e.message}")
            false
        }
    }

    suspend fun isUsernameAvailable(username: String): Boolean {
        val trimmed = username.trim().lowercase()
        if (trimmed.length < 3 || trimmed.length > 30) return false
        val regex = Regex("^[a-zA-Z0-9._]+$")
        if (!regex.matches(trimmed)) return false
        
        return try {
            val snapshot = firestore.collection("users")
                .whereEqualTo("username", trimmed)
                .limit(1)
                .get()
                .await()
            snapshot.isEmpty
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Error checking username", e)
            true
        }
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim().lowercase()).await()
    }

    suspend fun signUpWithFullProfile(
        email: String,
        pass: String,
        username: String,
        fullName: String,
        photoUrl: String,
        bio: String,
        gender: String
    ): String {
        val trimmedEmail = email.trim().lowercase()
        val result = auth.createUserWithEmailAndPassword(trimmedEmail, pass).await()
        val uid = result.user?.uid ?: throw IllegalStateException("Account created but user ID is unavailable")
        
        val user = hashMapOf(
            "uid" to uid,
            "id" to uid,
            "email" to trimmedEmail,
            "username" to username.trim(),
            "name" to fullName.trim(),
            "displayName" to fullName.trim(),
            "profilePicUrl" to photoUrl,
            "avatarUrl" to photoUrl,
            "bio" to bio.trim(),
            "gender" to gender,
            "createdAt" to System.currentTimeMillis(),
            "followerCount" to 0,
            "followingCount" to 0,
            "isVerified" to false,
            "isAdmin" to false,
            "status" to "online"
        )
        
        try {
            firestore.collection("users").document(uid).set(user).await()
            com.example.model.DatabaseInitializer.initializeUserSchemaSafe(uid)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Failed to save user profile due to rules", e)
        }
        return uid
    }

    suspend fun signInAsGuest() {
        val context = try { com.example.myAppInstance } catch (_: Exception) { null }
            ?: throw IllegalStateException("App context unavailable for guest login")

        val prefs = context.getSharedPreferences("guest_auth", android.content.Context.MODE_PRIVATE)
        var guestEmail = prefs.getString("guest_email", null)
        var guestPass = prefs.getString("guest_pass", null)

        if (guestEmail.isNullOrBlank() || guestPass.isNullOrBlank()) {
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: java.util.UUID.randomUUID().toString().take(8)
            guestEmail = "guest_${androidId.take(8)}@socialindia.com"
            guestPass = "guestPass_${androidId.take(8)}"
            prefs.edit()
                .putString("guest_email", guestEmail)
                .putString("guest_pass", guestPass)
                .apply()
        }

        try {
            val result = try {
                auth.signInWithEmailAndPassword(guestEmail, guestPass).await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                auth.createUserWithEmailAndPassword(guestEmail, guestPass).await()
            }

            val userId = result.user?.uid ?: throw IllegalStateException("Guest login succeeded without user ID")
            try {
                val doc = firestore.collection("users").document(userId).get().await()
                if (!doc.exists()) {
                    val user = hashMapOf(
                        "uid" to userId,
                        "username" to "Guest_${userId.take(5)}",
                        "profilePicUrl" to "",
                        "bio" to "Exploring the app as a guest."
                    )
                    firestore.collection("users").document(userId).set(user).await()
                    com.example.model.DatabaseInitializer.initializeUserSchemaSafe(userId)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Guest profile initialization skipped: ${e.message}")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (finalEx: Exception) {
            android.util.Log.e("AuthRepository", "Guest login failed", finalEx)
            throw finalEx
        }
    }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun getCurrentUserId(): String = auth.currentUser?.uid ?: ""

    suspend fun deactivateAccount(userId: String) {
        if (userId.isEmpty()) return
        try {
            firestore.collection("users").document(userId).update(
                mapOf(
                    "isDeactivated" to true,
                    "status" to "offline",
                    "deactivatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Error deactivating account", e)
        }
        try {
            auth.signOut()
        } catch (e: Exception) {
            // ignore
        }
    }

    suspend fun reactivateAccount(userId: String) {
        if (userId.isEmpty()) return
        try {
            firestore.collection("users").document(userId).update(
                mapOf(
                    "isDeactivated" to false,
                    "status" to "online",
                    "deactivatedAt" to null
                )
            ).await()
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Error reactivating account", e)
        }
    }

    suspend fun isAccountDeactivated(userId: String): Boolean {
        if (userId.isEmpty()) return false
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            doc.getBoolean("isDeactivated") ?: false
        } catch (e: Exception) {
            false
        }
    }
}
