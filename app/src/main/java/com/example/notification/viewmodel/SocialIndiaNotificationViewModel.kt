package com.example.notification.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.MultiAccountManager
import com.example.model.RepositoryManager
import com.example.notification.model.NotificationFilter
import com.example.notification.model.SocialIndiaNotification
import com.example.notification.repository.SocialIndiaNotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel for the Next-Generation Social India Notification Center.
 * Handles Real-time Firebase Sync, UID-Isolation across Multi-Account switches,
 * Date-based Sectioning (Today, This Week, Earlier), Filtering, and Search.
 */
class SocialIndiaNotificationViewModel(
    private val repository: SocialIndiaNotificationRepository = SocialIndiaNotificationRepository()
) : ViewModel() {

    private var listenJob: Job? = null
    private var unreadJob: Job? = null
    private var currentActiveUid: String = ""

    private val _rawNotifications = MutableStateFlow<List<SocialIndiaNotification>>(emptyList())
    val rawNotifications: StateFlow<List<SocialIndiaNotification>> = _rawNotifications

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    val selectedFilter = MutableStateFlow(NotificationFilter.ALL)
    val searchQuery = MutableStateFlow("")

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // Filtered and Searched stream
    val filteredNotifications: StateFlow<List<SocialIndiaNotification>> = combine(
        _rawNotifications,
        selectedFilter,
        searchQuery
    ) { notifications, filter, query ->
        var list = notifications

        // Apply Category Filter
        if (filter != NotificationFilter.ALL) {
            list = list.filter { it.type in filter.types }
        }

        // Apply Search Query with debouncing/trimming
        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            list = list.filter {
                it.actorUsername.lowercase().contains(q) ||
                it.actorDisplayName.lowercase().contains(q) ||
                it.text.lowercase().contains(q) ||
                it.type.lowercase().contains(q)
            }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Date-based Sectioning
    val todayNotifications: StateFlow<List<SocialIndiaNotification>> = filteredNotifications.map { list ->
        val startOfToday = getStartOfDayMillis(0)
        list.filter { it.createdAt >= startOfToday }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val thisWeekNotifications: StateFlow<List<SocialIndiaNotification>> = filteredNotifications.map { list ->
        val startOfToday = getStartOfDayMillis(0)
        val startOfWeek = getStartOfDayMillis(7)
        list.filter { it.createdAt in startOfWeek until startOfToday }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val earlierNotifications: StateFlow<List<SocialIndiaNotification>> = filteredNotifications.map { list ->
        val startOfWeek = getStartOfDayMillis(7)
        list.filter { it.createdAt < startOfWeek }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Monitor multi-account switches or session logins
        viewModelScope.launch {
            MultiAccountManager.activeAccount.collect { account ->
                val newUid = account?.uid ?: RepositoryManager.getCurrentUserId()
                if (newUid != currentActiveUid) {
                    currentActiveUid = newUid
                    bindAccount(newUid)
                }
            }
        }
    }

    private fun bindAccount(uid: String) {
        listenJob?.cancel()
        unreadJob?.cancel()

        if (uid.isEmpty()) {
            _rawNotifications.value = emptyList()
            _unreadCount.value = 0
            return
        }

        listenJob = viewModelScope.launch {
            repository.listenNotifications(uid).collect { list ->
                _rawNotifications.value = list
                _isRefreshing.value = false
            }
        }

        unreadJob = viewModelScope.launch {
            repository.listenUnreadCount(uid).collect { count ->
                _unreadCount.value = count
            }
        }
    }

    fun markAsRead(notificationId: String) {
        val uid = currentActiveUid.ifEmpty { RepositoryManager.getCurrentUserId() }
        viewModelScope.launch {
            repository.markAsRead(uid, notificationId)
        }
        _rawNotifications.value = _rawNotifications.value.map {
            if (it.id == notificationId) it.copy(read = true) else it
        }
    }

    fun markAllAsRead() {
        val uid = currentActiveUid.ifEmpty { RepositoryManager.getCurrentUserId() }
        viewModelScope.launch {
            repository.markAllAsRead(uid)
        }
        _rawNotifications.value = _rawNotifications.value.map { it.copy(read = true) }
        _unreadCount.value = 0
    }

    fun deleteNotification(notificationId: String) {
        val uid = currentActiveUid.ifEmpty { RepositoryManager.getCurrentUserId() }
        viewModelScope.launch {
            repository.deleteNotification(uid, notificationId)
        }
        _rawNotifications.value = _rawNotifications.value.filter { it.id != notificationId }
    }

    fun refresh() {
        _isRefreshing.value = true
        val uid = currentActiveUid.ifEmpty { RepositoryManager.getCurrentUserId() }
        bindAccount(uid)
    }

    private fun getStartOfDayMillis(daysAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
