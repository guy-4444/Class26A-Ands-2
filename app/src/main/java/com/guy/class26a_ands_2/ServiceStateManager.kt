package com.guy.class26a_ands_2

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized service state management with reactive updates.
 *
 * Uses StateFlow for UI observation - when state changes, UI updates automatically.
 *
 * This is a singleton object, so the state is shared across the entire app.
 */
object ServiceStateManager {

    private const val TAG = "ServiceStateManager"

    // ===== Service State =====
    private val _state = MutableStateFlow(ServiceState.STOPPED)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    // ===== Service Process Running =====
    private val _isServiceProcessRunning = MutableStateFlow(false)
    val isServiceProcessRunning: StateFlow<Boolean> = _isServiceProcessRunning.asStateFlow()

    // ===== Location Data =====
    private val _locationData = MutableStateFlow<LocationData?>(null)
    val locationData: StateFlow<LocationData?> = _locationData.asStateFlow()

    /**
     * Update the current state. Called by LocationService when state changes.
     */
    fun updateState(newState: ServiceState) {
        _state.value = newState
    }

    /**
     * Mark whether the service process is running. Called in Service.onCreate/onDestroy.
     */
    fun setServiceProcessRunning(running: Boolean) {
        _isServiceProcessRunning.value = running
    }

    /**
     * Update location data. Called by LocationService when new location is received.
     */
    fun updateLocation(data: LocationData) {
        _locationData.value = data
    }

    /**
     * Clear location data. Called when service stops.
     */
    fun clearLocation() {
        _locationData.value = null
    }

    /**
     * Attempt crash recovery when app comes to foreground.
     *
     * IMPORTANT: This can ONLY start service when called from foreground context
     * (like Activity.onResume). On Android 12+, starting foreground service from
     * background is restricted.
     *
     * @param context Must be an Activity context or called when app is in foreground
     * @return true if recovery was attempted, false if no recovery needed
     */
    fun checkAndRecoverIfNeeded(context: Context): Boolean {
        val needsRecovery = MyDB.needsRecovery(context)

        if (!needsRecovery) {
            Log.d(TAG, "No recovery needed")
            // Sync state from persisted data
            _state.value = MyDB.getActualState(context)
            return false
        }

        val desiredState = MyDB.getDesiredState(context)
        Log.d(TAG, "Recovery needed - desired state: $desiredState")

        return try {
            when (desiredState) {
                ServiceState.RUNNING -> {
                    LocationService.start(context)
                    true
                }
                ServiceState.PAUSED -> {
                    // Start first, then pause after short delay
                    LocationService.start(context)
                    MCT7.get().delay(500, "recover_pause") {
                        LocationService.pause(context)
                    }
                    true
                }
                ServiceState.STOPPED -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recover service", e)
            false
        }
    }
}