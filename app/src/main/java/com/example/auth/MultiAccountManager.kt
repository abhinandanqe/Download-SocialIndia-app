package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.model.RepositoryManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SavedAccount(
    val uid: String,
    val email: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val isVerified: Boolean = false,
    val encryptedPassword: String = "",
    val authType: String = "EMAIL_PASSWORD", // "EMAIL_PASSWORD", "GOOGLE", "GUEST"
    val addedAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)

object MultiAccountManager {
    private const val PREFS_NAME = "social_india_multi_accounts"
    private const val KEY_ACCOUNTS_JSON = "saved_accounts_list"
    private const val KEY_ACTIVE_UID = "active_account_uid"

    private val scope = CoroutineScope(Dispatchers.IO)
    private var prefs: SharedPreferences? = null

    private val _accountsList = MutableStateFlow<List<SavedAccount>>(emptyList())
    val accountsList: StateFlow<List<SavedAccount>> = _accountsList.asStateFlow()

    private val _activeAccount = MutableStateFlow<SavedAccount?>(null)
    val activeAccount: StateFlow<SavedAccount?> = _activeAccount.asStateFlow()

    // Incremented on every account switch to trigger seamless recomposition across the entire app
    private val _switchCounter = MutableStateFlow(0)
    val switchCounter: StateFlow<Int> = _switchCounter.asStateFlow()

    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadAccountsFromStorage(context)
        syncWithCurrentFirebaseAuth(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return prefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs = it }
    }

    private fun loadAccountsFromStorage(context: Context) {
        val p = getPrefs(context)
        val jsonStr = p.getString(KEY_ACCOUNTS_JSON, null)
        val list = mutableListOf<SavedAccount>()
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        SavedAccount(
                            uid = obj.optString("uid", ""),
                            email = obj.optString("email", ""),
                            username = obj.optString("username", ""),
                            displayName = obj.optString("displayName", ""),
                            avatarUrl = obj.optString("avatarUrl", ""),
                            isVerified = obj.optBoolean("isVerified", false),
                            encryptedPassword = obj.optString("encryptedPassword", ""),
                            authType = obj.optString("authType", "EMAIL_PASSWORD"),
                            addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
                            lastActiveAt = obj.optLong("lastActiveAt", System.currentTimeMillis())
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MultiAccountManager", "Error parsing saved accounts json", e)
            }
        }
        _accountsList.value = list

        val activeUid = p.getString(KEY_ACTIVE_UID, null)
        val selectedAccount = if (!activeUid.isNullOrEmpty()) {
            list.find { it.uid == activeUid } ?: list.firstOrNull()
        } else {
            list.firstOrNull()
        }
        _activeAccount.value = selectedAccount
        selectedAccount?.let {
            RepositoryManager.attachCurrentUserRealtimeListener(it.uid)
            com.example.security.AccountSuspensionManager.observeUserAccountStatus(it.uid)
        }
    }

    private fun persistAccounts(context: Context) {
        val p = getPrefs(context)
        val list = _accountsList.value
        val jsonArray = JSONArray()
        for (acc in list) {
            val obj = JSONObject().apply {
                put("uid", acc.uid)
                put("email", acc.email)
                put("username", acc.username)
                put("displayName", acc.displayName)
                put("avatarUrl", acc.avatarUrl)
                put("isVerified", acc.isVerified)
                put("encryptedPassword", acc.encryptedPassword)
                put("authType", acc.authType)
                put("addedAt", acc.addedAt)
                put("lastActiveAt", acc.lastActiveAt)
            }
            jsonArray.put(obj)
        }
        p.edit()
            .putString(KEY_ACCOUNTS_JSON, jsonArray.toString())
            .putString(KEY_ACTIVE_UID, _activeAccount.value?.uid)
            .apply()
    }

    /**
     * Synchronizes the currently signed-in Firebase user into the saved device accounts list
     * and refreshes remote profile info (username, display name, avatar, verification status).
     */
    fun syncWithCurrentFirebaseAuth(context: Context) {
        val currentFbUser = FirebaseAuth.getInstance().currentUser ?: return
        val currentUid = currentFbUser.uid

        scope.launch {
            try {
                // Fetch fresh profile from Firestore
                val doc = RepositoryManager.firestore.collection("users").document(currentUid).get().await()
                val username = doc.getString("username") ?: currentFbUser.displayName ?: "User"
                val displayName = doc.getString("name") ?: doc.getString("displayName") ?: username
                val avatarUrl = doc.getString("profilePicUrl") ?: doc.getString("avatar_url") ?: ""
                val isVerified = doc.getBoolean("isVerified") ?: false
                val email = currentFbUser.email ?: doc.getString("email") ?: ""

                val existingList = _accountsList.value.toMutableList()
                val existingIndex = existingList.indexOfFirst { it.uid == currentUid }

                val updatedAccount = if (existingIndex >= 0) {
                    existingList[existingIndex].copy(
                        username = username,
                        displayName = displayName,
                        avatarUrl = avatarUrl,
                        isVerified = isVerified,
                        email = if (email.isNotEmpty()) email else existingList[existingIndex].email,
                        lastActiveAt = System.currentTimeMillis()
                    )
                } else {
                    SavedAccount(
                        uid = currentUid,
                        email = email,
                        username = username,
                        displayName = displayName,
                        avatarUrl = avatarUrl,
                        isVerified = isVerified,
                        encryptedPassword = "",
                        authType = if (currentFbUser.isAnonymous) "GUEST" else "EMAIL_PASSWORD",
                        addedAt = System.currentTimeMillis(),
                        lastActiveAt = System.currentTimeMillis()
                    )
                }

                if (existingIndex >= 0) {
                    existingList[existingIndex] = updatedAccount
                } else {
                    existingList.add(updatedAccount)
                }

                withContext(Dispatchers.Main) {
                    _accountsList.value = existingList
                    _activeAccount.value = updatedAccount
                    persistAccounts(context)
                }
            } catch (e: Exception) {
                android.util.Log.e("MultiAccountManager", "Error syncing current firebase user", e)
            }
        }
    }

    /**
     * Records a successfully authenticated account into the local multi-account storage.
     * Passwords/credentials are securely encrypted with AndroidKeyStore before saving.
     */
    fun recordSuccessfulLogin(
        context: Context,
        uid: String,
        email: String,
        password: String?,
        username: String? = null,
        displayName: String? = null,
        avatarUrl: String? = null,
        isVerified: Boolean = false,
        authType: String = "EMAIL_PASSWORD"
    ) {
        scope.launch {
            try {
                val encryptedCred = if (!password.isNullOrEmpty()) {
                    AccountSecurityManager.encrypt(context, password)
                } else ""

                // Fetch latest Firestore profile if needed
                var finalUsername = username ?: ""
                var finalDisplayName = displayName ?: ""
                var finalAvatar = avatarUrl ?: ""
                var finalVerified = isVerified

                try {
                    val doc = RepositoryManager.firestore.collection("users").document(uid).get().await()
                    if (doc.exists()) {
                        if (finalUsername.isEmpty()) {
                            finalUsername = doc.getString("username") ?: ""
                        }
                        if (finalDisplayName.isEmpty()) {
                            finalDisplayName = doc.getString("name") ?: doc.getString("displayName") ?: finalUsername
                        }
                        if (finalAvatar.isEmpty()) {
                            finalAvatar = doc.getString("profilePicUrl") ?: doc.getString("avatar_url") ?: ""
                        }
                        finalVerified = doc.getBoolean("isVerified") ?: false
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MultiAccountManager", "Could not fetch user profile during login record", e)
                }

                if (finalUsername.isEmpty()) {
                    finalUsername = email.substringBefore("@")
                }
                if (finalDisplayName.isEmpty()) {
                    finalDisplayName = finalUsername
                }

                val currentList = _accountsList.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.uid == uid }

                val savedAccount = SavedAccount(
                    uid = uid,
                    email = email,
                    username = finalUsername,
                    displayName = finalDisplayName,
                    avatarUrl = finalAvatar,
                    isVerified = finalVerified,
                    encryptedPassword = if (encryptedCred.isNotEmpty()) encryptedCred else (if (existingIndex >= 0) currentList[existingIndex].encryptedPassword else ""),
                    authType = authType,
                    addedAt = if (existingIndex >= 0) currentList[existingIndex].addedAt else System.currentTimeMillis(),
                    lastActiveAt = System.currentTimeMillis()
                )

                if (existingIndex >= 0) {
                    currentList[existingIndex] = savedAccount
                } else {
                    currentList.add(savedAccount)
                }

                withContext(Dispatchers.Main) {
                    _accountsList.value = currentList
                    _activeAccount.value = savedAccount
                    persistAccounts(context)
                    _switchCounter.value += 1
                }
            } catch (e: Exception) {
                android.util.Log.e("MultiAccountManager", "Error recording successful login", e)
            }
        }
    }

    /**
     * Instantly switches to the specified account.
     * Re-authenticates with Firebase under the hood if needed.
     * Once switched, clears caches and triggers a global UI refresh.
     */
    fun switchAccount(
        context: Context,
        targetUid: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val account = _accountsList.value.find { it.uid == targetUid }
        if (account == null) {
            onResult(false, "Account not found on this device")
            return
        }

        val currentFbUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentFbUid == targetUid) {
            // Already active in Firebase
            _activeAccount.value = account
            persistAccounts(context)
            _switchCounter.value += 1
            onResult(true, null)
            return
        }

        _isSwitching.value = true

        scope.launch {
            try {
                if (account.authType == "EMAIL_PASSWORD" || (account.authType == "GOOGLE" && account.encryptedPassword.isNotEmpty())) {
                    val decryptedPass = AccountSecurityManager.decrypt(context, account.encryptedPassword)
                    if (decryptedPass.isNotEmpty() && account.email.isNotEmpty()) {
                        FirebaseAuth.getInstance().signOut()
                        FirebaseAuth.getInstance().signInWithEmailAndPassword(account.email, decryptedPass).await()
                    } else if (account.authType == "EMAIL_PASSWORD") {
                        withContext(Dispatchers.Main) {
                            _isSwitching.value = false
                            onResult(false, "Saved password expired. Please log in with password.")
                        }
                        return@launch
                    }
                } else if (account.authType == "GUEST") {
                    val guestPrefs = context.getSharedPreferences("guest_auth", android.content.Context.MODE_PRIVATE)
                    val gEmail = guestPrefs.getString("guest_email", null)
                    val gPass = guestPrefs.getString("guest_pass", null)
                    if (!gEmail.isNullOrBlank() && !gPass.isNullOrBlank()) {
                        FirebaseAuth.getInstance().signOut()
                        FirebaseAuth.getInstance().signInWithEmailAndPassword(gEmail, gPass).await()
                    } else {
                        withContext(Dispatchers.Main) {
                            _isSwitching.value = false
                            onResult(false, "Guest session expired. Please sign in again.")
                        }
                        return@launch
                    }
                }

                // Update active account and persist
                val updatedAccount = account.copy(lastActiveAt = System.currentTimeMillis())
                val updatedList = _accountsList.value.map {
                    if (it.uid == targetUid) updatedAccount else it
                }

                withContext(Dispatchers.Main) {
                    RepositoryManager.clearFollowCache()
                    _accountsList.value = updatedList
                    _activeAccount.value = updatedAccount
                    persistAccounts(context)
                    RepositoryManager.attachCurrentUserRealtimeListener(targetUid)
                    com.example.security.FaceLockManager.onAccountSwitched(context, targetUid)
                    com.example.security.AccountSuspensionManager.observeUserAccountStatus(targetUid)
                    _isSwitching.value = false
                    _switchCounter.value += 1
                    onResult(true, null)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MultiAccountManager", "Failed to switch account", e)
                withContext(Dispatchers.Main) {
                    _isSwitching.value = false
                    onResult(false, e.localizedMessage ?: "Failed to switch account. Please log in again.")
                }
            }
        }
    }

    /**
     * Removes an account from this device's Account Switcher only.
     * The cloud Firebase user account is NOT deleted.
     */
    fun removeAccountFromDevice(context: Context, uid: String) {
        val currentList = _accountsList.value.toMutableList()
        val index = currentList.indexOfFirst { it.uid == uid }
        if (index < 0) return

        currentList.removeAt(index)
        _accountsList.value = currentList

        val wasActive = _activeAccount.value?.uid == uid

        if (wasActive) {
            val nextAccount = currentList.firstOrNull()
            if (nextAccount != null) {
                switchAccount(context, nextAccount.uid) { _, _ -> }
            } else {
                _activeAccount.value = null
                FirebaseAuth.getInstance().signOut()
                persistAccounts(context)
                _switchCounter.value += 1
            }
        } else {
            persistAccounts(context)
        }
    }

    /**
     * Performs a standard logout for the current active account.
     * Keeps the account entry in the switcher so user can tap it to log back in.
     */
    fun logoutCurrentAccount(context: Context) {
        com.example.security.FaceLockManager.onLogout()
        com.example.security.AccountSuspensionManager.reset()
        FirebaseAuth.getInstance().signOut()
        _activeAccount.value = null
        persistAccounts(context)
        _switchCounter.value += 1
    }
}
