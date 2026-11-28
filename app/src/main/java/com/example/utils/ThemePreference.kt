package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ThemePreference {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_IS_DARK = "is_dark_theme_clean_white"

    private lateinit var prefs: SharedPreferences
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isDarkTheme.value = prefs.getBoolean(KEY_IS_DARK, false)
    }

    fun setDarkTheme(isDark: Boolean) {
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_IS_DARK, isDark).apply()
        }
        _isDarkTheme.value = isDark
    }
}
