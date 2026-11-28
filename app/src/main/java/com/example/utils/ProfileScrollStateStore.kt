package com.example.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * Production-ready in-memory session store for preserving profile scroll positions and header collapse offsets
 * across tab switches, screen navigations, and account switches.
 * Keyed uniquely by (activeAccountUid, targetProfileUid, tabIndex) to prevent cross-account or cross-user state leaks.
 */
object ProfileScrollStateStore {
    private val scrollPositions = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val headerOffsets = ConcurrentHashMap<String, Float>()

    fun makeKey(accountUid: String, profileUid: String, tabIndex: Int): String {
        val safeAcc = accountUid.ifEmpty { "current_user" }
        val safeProf = profileUid.ifEmpty { safeAcc }
        return "${safeAcc}_${safeProf}_tab$tabIndex"
    }

    fun makeHeaderKey(accountUid: String, profileUid: String): String {
        val safeAcc = accountUid.ifEmpty { "current_user" }
        val safeProf = profileUid.ifEmpty { safeAcc }
        return "${safeAcc}_${safeProf}_header"
    }

    fun savePosition(accountUid: String, profileUid: String, tabIndex: Int, index: Int, offset: Int) {
        val key = makeKey(accountUid, profileUid, tabIndex)
        scrollPositions[key] = Pair(index, offset)
    }

    fun getPosition(accountUid: String, profileUid: String, tabIndex: Int): Pair<Int, Int> {
        val key = makeKey(accountUid, profileUid, tabIndex)
        return scrollPositions[key] ?: Pair(0, 0)
    }

    fun saveHeaderOffset(accountUid: String, profileUid: String, offset: Float) {
        val key = makeHeaderKey(accountUid, profileUid)
        headerOffsets[key] = offset
    }

    fun getHeaderOffset(accountUid: String, profileUid: String): Float {
        val key = makeHeaderKey(accountUid, profileUid)
        return headerOffsets[key] ?: 0f
    }

    fun resetForProfile(accountUid: String, profileUid: String) {
        val safeAcc = accountUid.ifEmpty { "current_user" }
        val safeProf = profileUid.ifEmpty { safeAcc }
        val prefix = "${safeAcc}_${safeProf}"
        val toRemove = scrollPositions.keys.filter { it.startsWith(prefix) }
        toRemove.forEach { scrollPositions.remove(it) }
        headerOffsets.remove(makeHeaderKey(safeAcc, safeProf))
    }
}
