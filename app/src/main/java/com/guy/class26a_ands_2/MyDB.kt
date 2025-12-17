package com.guy.class26a_ands_2

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * MyDB - Simple persistence for service state using SharedPreferences.
 *
 * WHY DO WE NEED THIS?
 * - Android can kill our app/service at any time
 * - When app restarts, we need to know what state we should restore
 * - We track both "what user wants" (desired) and "what's happening" (actual)
 *
 * HEARTBEAT SYSTEM:
 * - Service periodically writes a timestamp ("I'm alive!")
 * - External monitors check if heartbeat is recent
 * - If heartbeat is old, service probably crashed
 */
object MyDB {

    private const val FILE_NAME = "SERVICE_PREFS"
    private const val KEY_DESIRED_STATE = "KEY_DESIRED_STATE"
    private const val KEY_ACTUAL_STATE = "KEY_ACTUAL_STATE"
    private const val KEY_LAST_HEARTBEAT = "KEY_LAST_HEARTBEAT"

    // How often service writes heartbeat (milliseconds)
    const val HEARTBEAT_INTERVAL_MS = 10_000L  // 10 seconds

    // How long before we consider service dead (3x interval = allows 2 missed beats)
    private const val HEARTBEAT_TIMEOUT_MS = HEARTBEAT_INTERVAL_MS * 3  // 30 seconds

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    // ===== Desired State =====
    // What the USER wants (persists across crashes/reboots)

    fun getDesiredState(context: Context): ServiceState {
        val value = prefs(context).getString(KEY_DESIRED_STATE, ServiceState.STOPPED.name)
        return ServiceState.fromString(value)
    }

    fun setDesiredState(context: Context, state: ServiceState) {
        prefs(context).edit { putString(KEY_DESIRED_STATE, state.name) }
    }

    // ===== Actual State =====
    // What the SERVICE is currently doing

    fun getActualState(context: Context): ServiceState {
        val value = prefs(context).getString(KEY_ACTUAL_STATE, ServiceState.STOPPED.name)
        return ServiceState.fromString(value)
    }

    fun setActualState(context: Context, state: ServiceState) {
        prefs(context).edit { putString(KEY_ACTUAL_STATE, state.name) }
    }

    // ===== Heartbeat =====
    // Service calls updateHeartbeat() periodically to prove it's alive

    fun updateHeartbeat(context: Context) {
        prefs(context).edit { putLong(KEY_LAST_HEARTBEAT, System.currentTimeMillis()) }
    }

    fun getLastHeartbeat(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_HEARTBEAT, 0L)
    }

    fun clearHeartbeat(context: Context) {
        prefs(context).edit { remove(KEY_LAST_HEARTBEAT) }
    }

    /**
     * Check if service is truly running (heartbeat within timeout).
     */
    fun isServiceAlive(context: Context): Boolean {
        val lastHeartbeat = getLastHeartbeat(context)
        if (lastHeartbeat == 0L) return false

        val elapsed = System.currentTimeMillis() - lastHeartbeat
        return elapsed < HEARTBEAT_TIMEOUT_MS
    }

    /**
     * Check if service should be running but isn't (crashed).
     * Returns true if: user wants service running AND service isn't alive.
     */
    fun needsRecovery(context: Context): Boolean {
        val desired = getDesiredState(context)
        val shouldBeActive = desired == ServiceState.RUNNING || desired == ServiceState.PAUSED
        return shouldBeActive && !isServiceAlive(context)
    }
}
