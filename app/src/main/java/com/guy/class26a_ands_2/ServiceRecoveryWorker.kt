package com.guy.class26a_ands_2

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Handles service recovery after boot or crash.
 * Using WorkManager is safer than starting service directly from BroadcastReceiver.
 */
class ServiceRecoveryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ServiceRecoveryWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Checking if service needs recovery")

        val desiredState = MyDB.getDesiredState(applicationContext)
        Log.d(TAG, "Desired state: $desiredState")

        when (desiredState) {
            ServiceState.RUNNING -> {
                Log.d(TAG, "Starting service")
                LocationService.start(applicationContext)
            }
            ServiceState.PAUSED -> {
                Log.d(TAG, "Starting service in paused state")
                LocationService.start(applicationContext)
                // Wait a bit then pause
                Thread.sleep(1000)
                LocationService.pause(applicationContext)
            }
            ServiceState.STOPPED -> {
                Log.d(TAG, "Service was stopped - not recovering")
            }
        }

        return Result.success()
    }
}