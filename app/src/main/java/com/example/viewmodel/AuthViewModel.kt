package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.model.User
import com.example.model.RepositoryManager

data class SignUpDraft(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullName: String = "",
    val username: String = "",
    val photoUri: android.net.Uri? = null,
    val bio: String = "",
    val gender: String = "Prefer not to say"
)

fun mapFirebaseErrorMessage(e: Throwable): String {
    val message = e.message ?: ""
    return when {
        e is com.google.firebase.auth.FirebaseAuthInvalidUserException || 
            message.contains("user-not-found", ignoreCase = true) -> {
            "No account found with this email. Please check or create a new account."
        }
        e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ||
            message.contains("wrong-password", ignoreCase = true) ||
            message.contains("invalid-credential", ignoreCase = true) -> {
            if (message.contains("password", ignoreCase = true) || message.contains("credential", ignoreCase = true)) {
                "Incorrect password. Please try again."
            } else {
                "Please enter a valid email address."
            }
        }
        e is com.google.firebase.auth.FirebaseAuthUserCollisionException ||
            message.contains("email-already-in-use", ignoreCase = true) -> {
            "That email is already registered. Try logging in."
        }
        e is com.google.firebase.auth.FirebaseAuthWeakPasswordException ||
            message.contains("weak-password", ignoreCase = true) -> {
            "Password is too weak. Please use at least 6 characters."
        }
        e is com.google.firebase.FirebaseNetworkException || 
            message.contains("network", ignoreCase = true) ||
            message.contains("connection", ignoreCase = true) -> {
            "Network error. Please check your internet connection."
        }
        message.contains("too-many-requests", ignoreCase = true) -> {
            "Too many failed attempts. Please wait a moment before trying again."
        }
        message.contains("user-disabled", ignoreCase = true) -> {
            "This account has been disabled. Please contact support."
        }
        else -> {
            "Something went wrong. Please check your details and try again."
        }
    }
}

class AuthViewModel(
    private val authRepositoryArg: AuthRepository? = null
) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val authRepository: AuthRepository
        get() = authRepositoryArg ?: try {
            com.example.model.RepositoryManager.authRepository
        } catch (_: Throwable) {
            AuthRepository(auth, com.google.firebase.firestore.FirebaseFirestore.getInstance())
        }
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(auth.currentUser?.let { user ->
        User(
            id = user.uid,
            username = user.displayName ?: "User",
            email = user.email ?: "",
            avatarUrl = ""
        )
    })
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _currentUser.value = User(
                    id = user.uid,
                    username = user.displayName ?: "User",
                    email = user.email ?: "",
                    avatarUrl = ""
                )
            } else {
                _currentUser.value = null
            }
        }
    }

    fun checkEmailAvailability(email: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val trimmed = email.trim().lowercase()
                if (trimmed.isEmpty()) {
                    onResult(false, "Please enter your email address.")
                    return@launch
                }
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
                    onResult(false, "Please enter a valid email address.")
                    return@launch
                }
                val isTaken = authRepository.isEmailRegistered(trimmed)
                if (isTaken) {
                    onResult(false, "This email is already registered. Please log in.")
                } else {
                    onResult(true, null)
                }
            } catch (e: Exception) {
                // If checking fails (e.g. network/rules), allow proceeding to final step
                onResult(true, null)
            }
        }
    }

    fun checkUsernameAvailability(username: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val trimmed = username.trim().lowercase()
                if (trimmed.length < 3) {
                    onResult(false, "Username must be at least 3 characters.")
                    return@launch
                }
                if (trimmed.length > 30) {
                    onResult(false, "Username must be 30 characters or less.")
                    return@launch
                }
                val regex = Regex("^[a-zA-Z0-9._]+$")
                if (!regex.matches(trimmed)) {
                    onResult(false, "Only letters, numbers, dots, and underscores allowed.")
                    return@launch
                }
                val isAvailable = authRepository.isUsernameAvailable(trimmed)
                if (isAvailable) {
                    onResult(true, null)
                } else {
                    onResult(false, "That username is already taken. Please try another.")
                }
            } catch (e: Exception) {
                onResult(false, "Could not check username. Please try again.")
            }
        }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val trimmed = email.trim().lowercase()
                if (trimmed.isEmpty()) {
                    onResult(false, "Please enter your email address.")
                    return@launch
                }
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
                    onResult(false, "Please enter a valid email address.")
                    return@launch
                }
                authRepository.sendPasswordReset(trimmed)
                onResult(true, "Password reset link sent! Check your email inbox.")
            } catch (e: Exception) {
                val friendly = mapFirebaseErrorMessage(e)
                onResult(false, friendly)
            }
        }
    }

    fun signUpWithFullProfile(
        draft: SignUpDraft,
        context: android.content.Context,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // Step 1: Upload photo if provided
                var photoUrl = ""
                if (draft.photoUri != null) {
                    try {
                        val uploaded = RepositoryManager.mediaRepository.uploadMedia(draft.photoUri)
                        if (!uploaded.isNullOrEmpty()) {
                            photoUrl = uploaded
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AuthViewModel", "Photo upload failed, proceeding with default", e)
                    }
                }

                // Step 2: Create Auth account & user profile
                val userId = authRepository.signUpWithFullProfile(
                    email = draft.email.trim(),
                    pass = draft.password,
                    username = draft.username.trim(),
                    fullName = draft.fullName.trim(),
                    photoUrl = photoUrl,
                    bio = draft.bio.trim(),
                    gender = draft.gender
                )

                // Step 3: Admin check
                val normalizedEmail = draft.email.trim().lowercase()
                if (normalizedEmail == "abhinandankumar7867@gmail.com" || normalizedEmail == "abhinandankumar7867@gamil.com" || normalizedEmail == "abhi1234@gmail.com") {
                    try {
                        RepositoryManager.firestore.collection("users").document(userId)
                            .update("isAdmin", true, "isVerified", true).await()
                    } catch (e: Exception) {
                        android.util.Log.e("AuthViewModel", "Failed to auto-admin on signup", e)
                    }
                }

                // Step 4: MultiAccountManager compatibility
                com.example.auth.MultiAccountManager.recordSuccessfulLogin(
                    context = context,
                    uid = userId,
                    email = draft.email.trim(),
                    password = draft.password,
                    username = draft.username.trim(),
                    displayName = draft.fullName.trim(),
                    avatarUrl = photoUrl,
                    authType = "EMAIL_PASSWORD"
                )

                _authState.value = AuthState.Success("Account created successfully")
                onSuccess(draft.username.trim())
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Error in signUpWithFullProfile", e)
                val friendly = mapFirebaseErrorMessage(e)
                _authState.value = AuthState.Error(friendly)
                onError(friendly)
            }
        }
    }

    fun signUp(email: String, pass: String, username: String, profilePicUrl: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                authRepository.signUp(email, pass, username)
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    val normalizedEmail = email.trim().lowercase()
                    if (normalizedEmail == "abhinandankumar7867@gmail.com" || normalizedEmail == "abhinandankumar7867@gamil.com" || normalizedEmail == "abhi1234@gmail.com") {
                        try {
                            RepositoryManager.firestore.collection("users").document(userId)
                                .update("isAdmin", true, "isVerified", true).await()
                        } catch (e: Exception) {
                            android.util.Log.e("AuthViewModel", "Failed to auto-admin on signup", e)
                        }
                    }
                }
                _authState.value = AuthState.Success("Account created successfully")
                val context = try { com.example.myAppInstance } catch(e: Exception) { null }
                if (context != null && userId != null) {
                    com.example.auth.MultiAccountManager.recordSuccessfulLogin(
                        context = context,
                        uid = userId,
                        email = email,
                        password = pass,
                        username = username,
                        authType = "EMAIL_PASSWORD"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Error in signUp", e)
                val friendly = mapFirebaseErrorMessage(e)
                _authState.value = AuthState.Error(friendly)
            }
        }
    }

    fun logIn(email: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val trimmedEmail = email.trim().lowercase()
                if (trimmedEmail.isEmpty()) {
                    _authState.value = AuthState.Error("Please enter your email.")
                    return@launch
                }
                if (pass.isEmpty()) {
                    _authState.value = AuthState.Error("Please enter your password.")
                    return@launch
                }

                val result = auth.signInWithEmailAndPassword(trimmedEmail, pass).await()
                val userId = result.user?.uid
                if (userId != null) {
                    try {
                        val docRef = RepositoryManager.firestore.collection("users").document(userId)
                        val doc = docRef.get().await()
                        val isBanned = doc.getBoolean("isBanned") ?: false
                        if (isBanned) {
                            auth.signOut()
                            _authState.value = AuthState.Error("This account has been banned.")
                            return@launch
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AuthViewModel", "Firestore check failed (probably rules)", e)
                    }
                }
                _authState.value = AuthState.Success("Logged in successfully")
                val context = try { com.example.myAppInstance } catch(e: Exception) { null }
                if (context != null && userId != null) {
                    com.example.auth.MultiAccountManager.recordSuccessfulLogin(
                        context = context,
                        uid = userId,
                        email = trimmedEmail,
                        password = pass,
                        authType = "EMAIL_PASSWORD"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Error in logIn", e)
                val friendly = mapFirebaseErrorMessage(e)
                _authState.value = AuthState.Error(friendly)
            }
        }
    }

    fun signInAsGuest() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                authRepository.signInAsGuest()
                val userId = auth.currentUser?.uid
                _authState.value = AuthState.Success("Logged in as Guest")
                val context = try { com.example.myAppInstance } catch(e: Exception) { null }
                if (context != null && userId != null) {
                    com.example.auth.MultiAccountManager.recordSuccessfulLogin(
                        context = context,
                        uid = userId,
                        email = "guest_${userId.take(5)}@socialindia.com",
                        password = null,
                        username = "Guest_${userId.take(5)}",
                        authType = "GUEST"
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Guest login failed")
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val userId = result.user?.uid
                if (userId != null) {
                    try {
                        val docRef = RepositoryManager.firestore.collection("users").document(userId)
                        val doc = docRef.get().await()
                        val normalizedEmail = (result.user?.email ?: "").trim().lowercase()
                        val isMasterAdmin = normalizedEmail == "abhinandankumar7867@gmail.com" || normalizedEmail == "abhinandankumar7867@gamil.com" || normalizedEmail == "abhi1234@gmail.com"
                        if (!doc.exists()) {
                            docRef.set(mapOf(
                                "id" to userId,
                                "username" to (result.user?.displayName ?: "Google User"),
                                "email" to (result.user?.email ?: ""),
                                "avatarUrl" to (result.user?.photoUrl?.toString() ?: ""),
                                "isAdmin" to isMasterAdmin,
                                "isVerified" to isMasterAdmin,
                                "createdAt" to System.currentTimeMillis()
                            )).await()
                        } else if (isMasterAdmin) {
                            docRef.update(mapOf("isAdmin" to true, "isVerified" to true)).await()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AuthViewModel", "Firestore check failed for Google Auth", e)
                    }
                }
                _authState.value = AuthState.Success("Google Sign-In successful")
                val context = try { com.example.myAppInstance } catch(e: Exception) { null }
                if (context != null && userId != null) {
                    com.example.auth.MultiAccountManager.recordSuccessfulLogin(
                        context = context,
                        uid = userId,
                        email = result.user?.email ?: "",
                        password = null,
                        username = result.user?.displayName ?: "Google User",
                        avatarUrl = result.user?.photoUrl?.toString() ?: "",
                        authType = "GOOGLE"
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Google Sign-In failed")
            }
        }
    }

    fun signInWithSelectedGoogleEmail(context: android.content.Context, email: String) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isEmpty()) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // 1. Check if this email has a saved password on this device in MultiAccountManager
                val savedAcc = com.example.auth.MultiAccountManager.accountsList.value.firstOrNull {
                    it.email.trim().equals(cleanEmail, ignoreCase = true)
                }
                if (savedAcc != null && savedAcc.encryptedPassword.isNotEmpty()) {
                    val savedPass = com.example.auth.AccountSecurityManager.decrypt(context, savedAcc.encryptedPassword)
                    if (savedPass.isNotEmpty()) {
                        try {
                            val res = auth.signInWithEmailAndPassword(cleanEmail, savedPass).await()
                            val uid = res.user?.uid
                            if (uid != null) {
                                com.example.auth.MultiAccountManager.recordSuccessfulLogin(
                                    context = context,
                                    uid = uid,
                                    email = cleanEmail,
                                    password = savedPass,
                                    username = savedAcc.username,
                                    displayName = savedAcc.displayName,
                                    avatarUrl = savedAcc.avatarUrl,
                                    authType = "EMAIL_PASSWORD"
                                )
                                _authState.value = AuthState.Success("Google Sign-In successful")
                                return@launch
                            }
                        } catch (_: Exception) {}
                    }
                }

                // 2. Deterministic Google Bridge Password for Firebase Email/Password provider when OAuth SHA-1 is not registered
                val bridgePassword = "GAuth#${cleanEmail.hashCode()}!SocialIndia"
                val derivedUsername = cleanEmail.substringBefore("@").replace(Regex("[^a-zA-Z0-9._]"), "_").take(24).ifEmpty { "user" }
                val derivedDisplayName = cleanEmail.substringBefore("@")
                    .replace(Regex("[0-9]+$"), "")
                    .replace(".", " ")
                    .replace("_", " ")
                    .trim()
                    .ifEmpty { derivedUsername }
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                var authedUserId: String? = null
                try {
                    val loginRes = auth.signInWithEmailAndPassword(cleanEmail, bridgePassword).await()
                    authedUserId = loginRes.user?.uid
                } catch (signInErr: Exception) {
                    try {
                        val createRes = auth.createUserWithEmailAndPassword(cleanEmail, bridgePassword).await()
                        authedUserId = createRes.user?.uid
                    } catch (_: Exception) {
                        // Email already exists with a custom user password; check if already signed in
                        if (auth.currentUser?.email?.equals(cleanEmail, ignoreCase = true) == true) {
                            authedUserId = auth.currentUser?.uid
                        }
                    }
                }

                if (authedUserId != null) {
                    val isMasterAdmin = cleanEmail == "abhinandankumar7867@gmail.com" || cleanEmail == "abhinandankumar7867@gamil.com" || cleanEmail == "abhi1234@gmail.com"
                    try {
                        val docRef = RepositoryManager.firestore.collection("users").document(authedUserId)
                        val doc = docRef.get().await()
                        if (!doc.exists()) {
                            docRef.set(
                                mapOf(
                                    "id" to authedUserId,
                                    "username" to derivedUsername,
                                    "name" to derivedDisplayName,
                                    "displayName" to derivedDisplayName,
                                    "email" to cleanEmail,
                                    "profilePicUrl" to "",
                                    "avatarUrl" to "",
                                    "isAdmin" to isMasterAdmin,
                                    "isVerified" to isMasterAdmin,
                                    "createdAt" to System.currentTimeMillis()
                                )
                            ).await()
                        } else if (isMasterAdmin) {
                            docRef.update(mapOf("isAdmin" to true, "isVerified" to true)).await()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AuthViewModel", "Profile sync warning for Google account", e)
                    }

                    com.example.auth.MultiAccountManager.recordSuccessfulLogin(
                        context = context,
                        uid = authedUserId,
                        email = cleanEmail,
                        password = bridgePassword,
                        username = derivedUsername,
                        displayName = derivedDisplayName,
                        authType = "GOOGLE"
                    )
                    _authState.value = AuthState.Success("Google Sign-In successful")
                } else {
                    _authState.value = AuthState.Error("This email ($cleanEmail) is registered with a custom password. Please enter your password to log in.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapFirebaseErrorMessage(e))
            }
        }
    }

    fun logOut() {
        auth.signOut()
        _authState.value = AuthState.Idle
    }

    fun setError(msg: String) {
        _authState.value = AuthState.Error(msg)
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }
}
