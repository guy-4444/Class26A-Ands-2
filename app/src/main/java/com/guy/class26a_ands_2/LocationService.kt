package com.guy.class26a_ands_2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class LocationService : Service() {

    companion object {
        private const val TAG = "LocationService"

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"

        private const val NOTIFICATION_ID = 170
        private const val CHANNEL_ID = "com.guy.class26a_ands_2.FOREGROUND_CHANNEL"

        fun start(context: Context) = sendCommand(context, ACTION_START)
        fun stop(context: Context) = sendCommand(context, ACTION_STOP)
        fun pause(context: Context) = sendCommand(context, ACTION_PAUSE)
        fun resume(context: Context) = sendCommand(context, ACTION_RESUME)

        private fun sendCommand(context: Context, action: String) {
            val intent = Intent(context, LocationService::class.java).apply {
                this.action = action
            }
            context.startForegroundService(intent)
        }
    }

    private var currentState = ServiceState.STOPPED
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var workCounter = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        ServiceStateManager.setServiceProcessRunning(true)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        // Start foreground immediately to avoid ANR
        startForegroundNotification()

        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_RESUME -> handleResume()
            ACTION_PAUSE -> handlePause()
            ACTION_STOP -> handleStop()
            else -> {
                // Service restarted by system - restore from desired state
                val desired = MyDB.getDesiredState(this)
                Log.d(TAG, "System restart - desired state: $desired")
                when (desired) {
                    ServiceState.RUNNING -> handleStart()
                    ServiceState.PAUSED -> handlePause()
                    ServiceState.STOPPED -> handleStop()
                }
            }
        }

        return START_STICKY
    }

    private fun handleStart() {
        if (currentState == ServiceState.RUNNING) return

        Log.d(TAG, "Starting work")
        currentState = ServiceState.RUNNING
        MyDB.setDesiredState(this, ServiceState.RUNNING)
        MyDB.setActualState(this, ServiceState.RUNNING)
        ServiceStateManager.updateState(ServiceState.RUNNING)

        startWork()
        updateNotificationContent("Running...")
    }

    private fun handlePause() {
        if (currentState == ServiceState.PAUSED) return

        Log.d(TAG, "Pausing work")
        currentState = ServiceState.PAUSED
        MyDB.setDesiredState(this, ServiceState.PAUSED)
        MyDB.setActualState(this, ServiceState.PAUSED)
        ServiceStateManager.updateState(ServiceState.PAUSED)

        stopWork()
        // Keep heartbeat running so we know service is alive
        startHeartbeat()
        updateNotificationContent("Paused")
    }

    private fun handleResume() {
        if (currentState == ServiceState.RUNNING) return
        Log.d(TAG, "Resuming work")
        handleStart()
    }

    private fun handleStop() {
        Log.d(TAG, "Stopping service")
        currentState = ServiceState.STOPPED
        MyDB.setDesiredState(this, ServiceState.STOPPED)
        MyDB.setActualState(this, ServiceState.STOPPED)
        MyDB.clearHeartbeat(this)
        ServiceStateManager.updateState(ServiceState.STOPPED)

        stopWork()
        stopHeartbeat()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startWork() {
        stopWork() // Clear any existing

        // Main work task
        MCT6.get().cycle(object : MCT6.CycleTicker {
            override fun secondly(repeatsRemaining: Int) {
                Log.d(TAG, "Working: ${workCounter++}")
                // Your actual work here (location updates, etc.)
            }
            override fun done() {}
        }, MCT6.CONTINUOUSLY_REPEATS, 1000, TAG)

        startHeartbeat()
    }

    private fun stopWork() {
        MCT6.get().removeAllByTag(TAG)
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        MCT6.get().cycle(object : MCT6.CycleTicker {
            override fun secondly(repeatsRemaining: Int) {
                MyDB.updateHeartbeat(this@LocationService)
            }
            override fun done() {}
        }, MCT6.CONTINUOUSLY_REPEATS, MyDB.HEARTBEAT_INTERVAL_MS.toInt(), "${TAG}_heartbeat")
    }

    private fun stopHeartbeat() {
        MCT6.get().removeAllByTag("${TAG}_heartbeat")
    }

    // region Notification

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when location tracking is active"
            enableLights(true)
            lightColor = Color.BLUE
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_cycling)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_round))
            .setContentTitle("Location Service")
            .setContentText("Initializing...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notificationBuilder!!.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notificationBuilder!!.build())
        }
    }

    private fun updateNotificationContent(content: String) {
        notificationBuilder?.let {
            it.setContentText(content)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, it.build())
        }
    }

    // endregion

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        ServiceStateManager.setServiceProcessRunning(false)
        MCT6.get().removeAllByTag(TAG)
        MCT6.get().removeAllByTag("${TAG}_heartbeat")
        MyDB.setActualState(this, ServiceState.STOPPED)
    }
}