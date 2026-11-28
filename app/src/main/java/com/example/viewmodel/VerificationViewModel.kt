package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class VerificationUiState {
    object Idle : VerificationUiState()
    data class Loading(val message: String) : VerificationUiState()
    data class Success(val message: String = "Operation completed successfully!") : VerificationUiState()
    data class Error(val message: String) : VerificationUiState()
}

class VerificationViewModel : ViewModel() {

    private val repo = RepositoryManager.verificationRepository

    private val _uiState = MutableStateFlow<VerificationUiState>(VerificationUiState.Idle)
    val uiState: StateFlow<VerificationUiState> = _uiState.asStateFlow()

    private val _currentRequest = MutableStateFlow<VerificationRequest?>(null)
    val currentRequest: StateFlow<VerificationRequest?> = _currentRequest.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _plans = MutableStateFlow<List<VerificationPlan>>(emptyList())
    val plans: StateFlow<List<VerificationPlan>> = _plans.asStateFlow()

    private val _settings = MutableStateFlow(VerificationSettings())
    val settings: StateFlow<VerificationSettings> = _settings.asStateFlow()

    private val _supportTickets = MutableStateFlow<List<VerificationSupportTicket>>(emptyList())
    val supportTickets: StateFlow<List<VerificationSupportTicket>> = _supportTickets.asStateFlow()

    init {
        loadData()
    }

    fun refresh() = loadData()

    fun loadData() {
        val currentUserId = RepositoryManager.getCurrentUserId()
        if (currentUserId.isNotEmpty()) {
            viewModelScope.launch {
                // Check and enforce expiration
                repo.checkAndEnforceExpiration(currentUserId)

                // Load User
                try {
                    val userDoc = FirebaseFirestore.getInstance().collection("users").document(currentUserId).get().await()
                    if (userDoc.exists()) {
                        _currentUser.value = try {
                            userDoc.toObject(User::class.java)
                        } catch (_: Exception) {
                            User(
                                id = userDoc.id,
                                username = (userDoc.get("username") as? String) ?: "User",
                                displayName = (userDoc.get("name") as? String) ?: (userDoc.get("displayName") as? String) ?: "User",
                                email = (userDoc.get("email") as? String) ?: "",
                                avatarUrl = (userDoc.get("profilePicUrl") as? String) ?: (userDoc.get("avatarUrl") as? String) ?: "",
                                isVerified = (userDoc.get("isVerified") as? Boolean) ?: false
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Listen to Verification Request
                repo.getUserVerificationRequest(currentUserId).collect { req ->
                    _currentRequest.value = req
                }
            }

            viewModelScope.launch {
                repo.getUserSupportTickets(currentUserId).collect { tickets ->
                    _supportTickets.value = tickets
                }
            }
        }

        viewModelScope.launch {
            repo.getVerificationPlans().collect { plansList ->
                _plans.value = plansList
            }
        }

        viewModelScope.launch {
            repo.getVerificationSettings().collect { s ->
                _settings.value = s
            }
        }
    }

    fun resetState() {
        _uiState.value = VerificationUiState.Idle
    }

    fun submitApplication(
        fullName: String,
        email: String,
        phone: String,
        country: String,
        category: String,
        verificationType: String,
        businessName: String = "",
        businessCategory: String = "",
        businessContact: String = "",
        businessEmail: String = "",
        businessPhone: String = "",
        businessWebsite: String = "",
        businessAddress: String = "",
        businessRegistrationNumber: String = "",
        reason: String,
        documentUri: Uri?,
        selfieUri: Uri?,
        businessDocUri: Uri?,
        selectedPlan: VerificationPlan
    ) {
        val uid = RepositoryManager.getCurrentUserId()
        if (uid.isEmpty()) {
            _uiState.value = VerificationUiState.Error("Please log in to apply.")
            return
        }

        viewModelScope.launch {
            _uiState.value = VerificationUiState.Loading("Uploading identity files & submitting application...")
            val user = _currentUser.value
            val res = repo.submitApplication(
                userId = uid,
                username = user?.username ?: "user",
                userAvatarUrl = user?.avatarUrl ?: "",
                fullName = fullName,
                email = email,
                phone = phone,
                country = country,
                category = category,
                verificationType = verificationType,
                businessName = businessName,
                businessCategory = businessCategory,
                businessContact = businessContact,
                businessEmail = businessEmail,
                businessPhone = businessPhone,
                businessWebsite = businessWebsite,
                businessAddress = businessAddress,
                businessRegistrationNumber = businessRegistrationNumber,
                reason = reason,
                documentUri = documentUri,
                selfieUri = selfieUri,
                businessDocUri = businessDocUri,
                selectedPlan = selectedPlan
            )

            if (res.isSuccess) {
                _uiState.value = VerificationUiState.Success("Verification application submitted successfully!")
                loadData()
            } else {
                _uiState.value = VerificationUiState.Error(res.exceptionOrNull()?.message ?: "Failed to submit application.")
            }
        }
    }

    fun submitPayment(
        paymentReference: String,
        proofUri: Uri?
    ) {
        val uid = RepositoryManager.getCurrentUserId()
        val req = _currentRequest.value
        if (uid.isEmpty() || req == null) {
            _uiState.value = VerificationUiState.Error("No active verification request found.")
            return
        }

        if (paymentReference.trim().isEmpty()) {
            _uiState.value = VerificationUiState.Error("Please enter Transaction Reference ID.")
            return
        }

        viewModelScope.launch {
            _uiState.value = VerificationUiState.Loading("Submitting payment reference...")
            val res = repo.submitPaymentDetails(
                requestId = req.id,
                userId = uid,
                paymentReference = paymentReference.trim(),
                proofUri = proofUri
            )

            if (res.isSuccess) {
                _uiState.value = VerificationUiState.Success("Payment details submitted. Waiting for admin manual verification.")
                loadData()
            } else {
                _uiState.value = VerificationUiState.Error(res.exceptionOrNull()?.message ?: "Failed to submit payment details.")
            }
        }
    }

    fun submitRenewal(selectedPlan: VerificationPlan) {
        val uid = RepositoryManager.getCurrentUserId()
        val prevReq = _currentRequest.value
        if (uid.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = VerificationUiState.Loading("Creating renewal request...")
            val res = repo.submitRenewal(
                userId = uid,
                previousVerificationId = prevReq?.id ?: "",
                selectedPlan = selectedPlan
            )

            if (res.isSuccess) {
                _uiState.value = VerificationUiState.Success("Renewal request initiated. Please submit payment reference.")
                loadData()
            } else {
                _uiState.value = VerificationUiState.Error(res.exceptionOrNull()?.message ?: "Failed to initiate renewal.")
            }
        }
    }

    fun createSupportTicket(subject: String, message: String, category: String) {
        val uid = RepositoryManager.getCurrentUserId()
        val username = _currentUser.value?.username ?: "user"
        if (uid.isEmpty() || subject.trim().isEmpty() || message.trim().isEmpty()) {
            _uiState.value = VerificationUiState.Error("Please enter ticket subject and description.")
            return
        }

        viewModelScope.launch {
            _uiState.value = VerificationUiState.Loading("Submitting support ticket...")
            val res = repo.createSupportTicket(
                userId = uid,
                username = username,
                subject = subject.trim(),
                message = message.trim(),
                category = category
            )

            if (res.isSuccess) {
                _uiState.value = VerificationUiState.Success("Priority ticket submitted! Our support team will respond shortly.")
            } else {
                _uiState.value = VerificationUiState.Error(res.exceptionOrNull()?.message ?: "Failed to create support ticket.")
            }
        }
    }
}
