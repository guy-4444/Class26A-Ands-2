package com.guy.class26a_ands_2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * ServiceMonitorWorker - Monitors if service crashed and notifies user.
 *
 * WHY USE WORKMANAGER?
 * - WorkManager tasks survive app crashes and device reboots
 * - Runs even when app is not in foreground
 * - Perfect for periodic monitoring tasks
 *
 * WHAT IT DOES:
 * 1. Runs every 15 minutes (minimum allowed by WorkManager)
 * 2. Checks if service should be running but isn't (crashed)
 * 3. Shows notification asking user to restart (can't auto-restart on Android 12+)
 *
 * ANDROID 12+ LIMITATION:
 * - WorkManager CANNOT start foreground services from background
 * - We can only notify user and let them tap to restart
 * - This is by design to improve user privacy and battery life
 */
class ServiceMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ServiceMonitor"
        private const val WORK_NAME = "service_monitor_work"
        private const val WARNING_CHANNEL_ID = "com.guy.class26a_ands_2.WARNING_CHANNEL"
        private const val WARNING_NOTIFICATION_ID = 171

        // Action that MainActivity handles to restart service
        const val ACTION_RESTART_SERVICE = "com.guy.class26a_ands_2.ACTION_RESTART_SERVICE"

        /**
         * Schedule the periodic monitor. Call once during app startup.
         */
        fun schedule(context: Context) {
            Log.d(TAG, "Scheduling periodic monitor")

            // Minimum interval is 15 minutes
            val request = PeriodicWorkRequestBuilder<ServiceMonitorWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Don't replace if already scheduled
                request
            )
        }

        /**
         * Cancel the monitor. Call if you want to stop monitoring.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running monitor check")

        val desiredState = MyDB.getDesiredState(applicationContext)
        val isAlive = MyDB.isServiceAlive(applicationContext)

        Log.d(TAG, "Desired: $desiredState, Alive: $isAlive")

        when {
            // Service crashed - show notification
            MyDB.needsRecovery(applicationContext) -> {
                Log.d(TAG, "Service crashed - showing restart notification")
                showRestartNotification()
            }
            // User stopped service - dismiss any warning
            desiredState == ServiceState.STOPPED -> {
                dismissWarningNotification()
            }
            // All good - dismiss any old warnings
            else -> {
                dismissWarningNotification()
            }
        }

        return Result.success()
    }

    /**
     * Shows notification asking user to restart the service.
     * User must tap to restart - we cannot auto-restart from background.
     */
    private fun showRestartNotification() {
        createWarningChannel()

        // Intent to open MainActivity with restart action
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = ACTION_RESTART_SERVICE
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, WARNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cycling)
            .setContentTitle("Service Stopped")
            .setContentText("Tap to restart the location service")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("The location service stopped unexpectedly. Tap this notification to restart it."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)  // Dismiss when tapped
            .build()

        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(WARNING_NOTIFICATION_ID, notification)
    }

    private fun dismissWarningNotification() {
        applicationContext.getSystemService(NotificationManager::class.java)
            .cancel(WARNING_NOTIFICATION_ID)
    }

    private fun createWarningChannel() {
        val channel = NotificationChannel(
            WARNING_CHANNEL_ID,
            "Service Warnings",
            NotificationManager.IMPORTANCE_HIGH  // Makes notification more prominent
        ).apply {
            description = "Warnings when service stops unexpectedly"
        }
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
