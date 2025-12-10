package com.guy.class26a_ands_2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Handles device boot to restart service if it was running.
 *
 * BOOT_COMPLETED IS an exemption from background start restrictions.
 * However, on Android 14+ there are restrictions for certain foreground service types.
 *
 * For location services: Should work, but test on target devices.
 *
 * @see https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed - checking service state")

        // Schedule the monitor worker
        ServiceMonitorWorker.schedule(context)

        // Check if service should be running
        val desiredState = MyDB.getDesiredState(context)
        Log.d(TAG, "Desired state after boot: $desiredState")

        // BOOT_COMPLETED is an exemption - we CAN start foreground service here
        // But on Android 14+ there may be restrictions for location type services
        when (desiredState) {
            ServiceState.RUNNING -> {
                Log.d(TAG, "Starting service after boot")
                try {
                    LocationService.start(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start service after boot", e)
                    // Will be caught by ServiceMonitorWorker which shows notification
                }
            }
            ServiceState.PAUSED -> {
                Log.d(TAG, "Starting service in paused state after boot")
                try {
                    LocationService.start(context)
                    // Pause after a short delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        LocationService.pause(context)
                    }, 1000)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start service after boot", e)
                }
            }
            ServiceState.STOPPED -> {
                Log.d(TAG, "Service was stopped - not restarting")
            }
        }
    }
}