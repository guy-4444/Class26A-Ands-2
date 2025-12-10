package com.guy.class26a_ands_2

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized service state management with reactive updates.
 */
object ServiceStateManager {

    private const val TAG = "ServiceStateManager"

    private val _state = MutableStateFlow(ServiceState.STOPPED)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    private val _isServiceProcessRunning = MutableStateFlow(false)
    val isServiceProcessRunning: StateFlow<Boolean> = _isServiceProcessRunning.asStateFlow()

    fun updateState(newState: ServiceState) {
        _state.value = newState
    }

    fun setServiceProcessRunning(running: Boolean) {
        _isServiceProcessRunning.value = running
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

        // We're being called from foreground (Activity), so we CAN start service
        return try {
            when (desiredState) {
                ServiceState.RUNNING -> {
                    LocationService.start(context)
                    true
                }
                ServiceState.PAUSED -> {
                    LocationService.start(context)
                    MCT6.get().single({
                        LocationService.pause(context)
                    }, 500, "recover_pause")
                    true
                }
                ServiceState.STOPPED -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recover service", e)
            false
        }
    }

    /**
     * Check if auto-restart from background is possible.
     * Returns true if battery optimization is disabled for the app.
     */
    fun canAutoRestartFromBackground(context: Context): Boolean {
        return BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }
}