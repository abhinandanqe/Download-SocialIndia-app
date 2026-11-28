package com.example.utils

import java.util.Locale

object NumberFormatUtils {
    fun formatCount(count: Int): String {
        return formatCount(count.toLong())
    }

    fun formatCount(count: Long): String {
        val absVal = if (count < 0) -count else count
        val sign = if (count < 0) "-" else ""
        return when {
            absVal >= 1_000_000_000L -> {
                val b = absVal / 1_000_000_000.0
                if (absVal < 10_000_000_000L) {
                    "$sign" + String.format(Locale.US, "%.1fB", b).replace(".0B", "B")
                } else {
                    "$sign${(absVal / 1_000_000_000L)}B"
                }
            }
            absVal >= 1_000_000L -> {
                val m = absVal / 1_000_000.0
                if (absVal < 10_000_000L) {
                    "$sign" + String.format(Locale.US, "%.1fM", m).replace(".0M", "M")
                } else {
                    "$sign${(absVal / 1_000_000L)}M"
                }
            }
            absVal >= 1_000L -> {
                val k = absVal / 1_000.0
                if (absVal < 10_000L) {
                    "$sign" + String.format(Locale.US, "%.1fK", k).replace(".0K", "K")
                } else {
                    "$sign${(absVal / 1_000L)}K"
                }
            }
            else -> "$sign$absVal"
        }
    }
}
