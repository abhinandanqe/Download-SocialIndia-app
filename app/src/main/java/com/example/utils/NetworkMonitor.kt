package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NetworkMonitor {
    private const val PREFS_NAME = "social_india_perf_prefs"
    private const val KEY_DATA_SAVER = "smart_data_saver_enabled"
    private const val KEY_AUTO_LITE_MODE = "auto_lite_mode_enabled"

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isDataSaverEnabled = MutableStateFlow(false)
    val isDataSaverEnabled: StateFlow<Boolean> = _isDataSaverEnabled.asStateFlow()

    private val _isAutoLiteModeEnabled = MutableStateFlow(true)
    val isAutoLiteModeEnabled: StateFlow<Boolean> = _isAutoLiteModeEnabled.asStateFlow()

    private val _isSlowNetwork = MutableStateFlow(false)
    val isSlowNetwork: StateFlow<Boolean> = _isSlowNetwork.asStateFlow()

    private val _isMeteredNetwork = MutableStateFlow(false)
    val isMeteredNetwork: StateFlow<Boolean> = _isMeteredNetwork.asStateFlow()

    private val _networkSpeedKbps = MutableStateFlow(15000)
    val networkSpeedKbps: StateFlow<Int> = _networkSpeedKbps.asStateFlow()

    private val _effectiveLiteMode = MutableStateFlow(false)
    val effectiveLiteMode: StateFlow<Boolean> = _effectiveLiteMode.asStateFlow()

    fun isLiteModeActive(): Boolean = _effectiveLiteMode.value

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _isDataSaverEnabled.value = prefs.getBoolean(KEY_DATA_SAVER, false)
            _isAutoLiteModeEnabled.value = prefs.getBoolean(KEY_AUTO_LITE_MODE, true)

            val connectivityManager =
                context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return

            _isOnline.value = checkCurrentConnectivity(connectivityManager)
            updateNetworkQuality(connectivityManager)

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isOnline.value = true
                        updateNetworkQuality(connectivityManager)
                    }

                    override fun onLost(network: Network) {
                        _isOnline.value = checkCurrentConnectivity(connectivityManager)
                        updateNetworkQuality(connectivityManager)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        _isOnline.value = hasInternet
                        evaluateCapabilities(networkCapabilities)
                    }
                }
            )
        } catch (e: Exception) {
            _isOnline.value = true
        }
    }

    fun setDataSaverEnabled(context: Context, enabled: Boolean) {
        _isDataSaverEnabled.value = enabled
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DATA_SAVER, enabled)
                .apply()
        } catch (_: Exception) {}
        recomputeEffectiveLiteMode()
    }

    fun setAutoLiteModeEnabled(context: Context, enabled: Boolean) {
        _isAutoLiteModeEnabled.value = enabled
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_LITE_MODE, enabled)
                .apply()
        } catch (_: Exception) {}
        recomputeEffectiveLiteMode()
    }

    private fun updateNetworkQuality(cm: ConnectivityManager) {
        try {
            val activeNetwork = cm.activeNetwork
            val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
            if (caps != null) {
                evaluateCapabilities(caps)
            } else {
                recomputeEffectiveLiteMode()
            }
        } catch (_: Exception) {
            recomputeEffectiveLiteMode()
        }
    }

    private fun evaluateCapabilities(caps: NetworkCapabilities) {
        try {
            val downBandwidth = caps.linkDownstreamBandwidthKbps
            _networkSpeedKbps.value = downBandwidth.coerceAtLeast(100)
            val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            _isMeteredNetwork.value = isMetered
            val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            // Treat < 1800 Kbps downstream or constrained cellular as slow network
            _isSlowNetwork.value = (downBandwidth in 1..1800) || (isCellular && downBandwidth in 1..3000)
            recomputeEffectiveLiteMode()
        } catch (_: Exception) {
            recomputeEffectiveLiteMode()
        }
    }

    private fun recomputeEffectiveLiteMode() {
        _effectiveLiteMode.value = _isDataSaverEnabled.value || (_isAutoLiteModeEnabled.value && _isSlowNetwork.value)
    }

    fun recheck(context: Context): Boolean {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val online = if (cm != null) {
                updateNetworkQuality(cm)
                checkCurrentConnectivity(cm)
            } else true
            _isOnline.value = online
            online
        } catch (e: Exception) {
            true
        }
    }

    private fun checkCurrentConnectivity(cm: ConnectivityManager): Boolean {
        return try {
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }
}
