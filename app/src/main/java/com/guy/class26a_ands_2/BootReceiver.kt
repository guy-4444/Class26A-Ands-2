package com.guy.class26a_ands_2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * BootReceiver - Restarts service after device boot.
 *
 * WHY IS THIS NEEDED?
 * - When device reboots, all services are stopped
 * - This receiver detects boot completion and restarts our service
 * - Only restarts if user had the service running before reboot
 *
 * MANIFEST REQUIREMENTS:
 * - Permission: android.permission.RECEIVE_BOOT_COMPLETED
 * - Receiver registered with intent-filter for BOOT_COMPLETED
 *
 * ANDROID 12+ NOTE:
 * - BOOT_COMPLETED is an exemption from background start restrictions
 * - We CAN start foreground services from this receiver
 *
 * @see https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Only handle boot completed
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed - checking service state")

        // Re-schedule the service monitor worker
        ServiceMonitorWorker.schedule(context)

        // Check if service should be restarted
        val desiredState = MyDB.getDesiredState(context)
        Log.d(TAG, "Desired state after boot: $desiredState")

        when (desiredState) {
            ServiceState.RUNNING -> {
                Log.d(TAG, "Starting service after boot")
                tryStartService(context, restorePaused = false)
            }
            ServiceState.PAUSED -> {
                Log.d(TAG, "Starting service in paused state after boot")
                tryStartService(context, restorePaused = true)
            }
            ServiceState.STOPPED -> {
                Log.d(TAG, "Service was stopped by user - not restarting")
            }
        }
    }

    private fun tryStartService(context: Context, restorePaused: Boolean) {
        try {
            LocationService.start(context)

            if (restorePaused) {
                // Pause after short delay to let service initialize
                Handler(Looper.getMainLooper()).postDelayed({
                    LocationService.pause(context)
                }, 1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service after boot", e)
            // ServiceMonitorWorker will show notification asking user to restart
        }
    }
}
