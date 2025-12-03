package com.guy.class26a_ands_2

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object MyDB {

    private const val FILE_NAME = "SERVICE_PREFS"
    private const val KEY_DESIRED_STATE = "KEY_DESIRED_STATE"
    private const val KEY_ACTUAL_STATE = "KEY_ACTUAL_STATE"
    private const val KEY_LAST_HEARTBEAT = "KEY_LAST_HEARTBEAT"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    // Desired state - what the user wants (persists across crashes/reboots)
    fun getDesiredState(context: Context): ServiceState {
        val value = prefs(context).getString(KEY_DESIRED_STATE, ServiceState.STOPPED.name)
        return ServiceState.fromString(value)
    }

    fun setDesiredState(context: Context, state: ServiceState) {
        prefs(context).edit { putString(KEY_DESIRED_STATE, state.name) }
    }

    // Actual state - what the service is currently doing
    fun getActualState(context: Context): ServiceState {
        val value = prefs(context).getString(KEY_ACTUAL_STATE, ServiceState.STOPPED.name)
        return ServiceState.fromString(value)
    }

    fun setActualState(context: Context, state: ServiceState) {
        prefs(context).edit { putString(KEY_ACTUAL_STATE, state.name) }
    }

    // Heartbeat - service updates this periodically to prove it's alive
    fun updateHeartbeat(context: Context) {
        prefs(context).edit { putLong(KEY_LAST_HEARTBEAT, System.currentTimeMillis()) }
    }

    fun getLastHeartbeat(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_HEARTBEAT, 0L)
    }

    // Check if service is truly running (heartbeat within last 30 seconds)
    fun isServiceAlive(context: Context): Boolean {
        val lastHeartbeat = getLastHeartbeat(context)
        val now = System.currentTimeMillis()
        return (now - lastHeartbeat) < 30_000L
    }

    // Check if service should be running but isn't
    fun needsRestart(context: Context): Boolean {
        val desired = getDesiredState(context)
        return (desired == ServiceState.RUNNING || desired == ServiceState.PAUSED) && !isServiceAlive(context)
    }

    fun clearAll(context: Context) {
        prefs(context).edit { clear() }
    }
}