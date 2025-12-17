package com.guy.class26a_ands_2

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority


/**
 * LocationService - A foreground service with FusedLocationProviderClient.
 *
 * WHY FOREGROUND SERVICE?
 * - Regular services can be killed by Android when resources are low
 * - Foreground services show a notification and get higher priority
 * - Required for location tracking, music playback, file downloads, etc.
 *
 * FUSED LOCATION PROVIDER:
 * - Google Play Services API for location
 * - Combines GPS, WiFi, and cell tower data
 * - More battery efficient than raw GPS
 * - Automatically chooses best location source
 *
 * DATA FLOW TO UI:
 * - Location received → LocationData created → ServiceStateManager.updateLocation()
 * - MainActivity observes ServiceStateManager.locationData StateFlow
 * - UI updates automatically when new location arrives
 *
 * USAGE:
 *   LocationService.start(context)  // Start the service
 *   LocationService.pause(context)  // Pause work (keep service alive)
 *   LocationService.resume(context) // Resume work
 *   LocationService.stop(context)   // Stop completely
 */
class LocationService : Service() {

    companion object {
        private const val TAG = "LocationService"

        // Actions for controlling the service
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"

        // Notification constants
        private const val NOTIFICATION_ID = 170
        private const val CHANNEL_ID = "com.guy.class26a_ands_2.FOREGROUND_CHANNEL"

        // Wake lock tag (for debugging in battery stats)
        private const val WAKE_LOCK_TAG = "LocationService::WakeLock"

        // Location settings
        private const val LOCATION_INTERVAL_MS = 5000L      // Request location every 5 seconds
        private const val LOCATION_MIN_INTERVAL_MS = 2000L  // Fastest update interval

        // Convenience methods for controlling the service
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

    // ===== Service State =====

    private var currentState = ServiceState.STOPPED
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var locationCounter = 0

    // Wake lock for keeping CPU alive during work (optional for location-only)
    private var wakeLock: PowerManager.WakeLock? = null

    // Location services
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null

    // ===== Lifecycle =====

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        ServiceStateManager.setServiceProcessRunning(true)
        createNotificationChannel()
        initLocationClient()
    }

    /**
     * Initialize FusedLocationProviderClient and LocationCallback.
     */
    private fun initLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    onLocationReceived(location)
                }
            }
        }
    }

    /**
     * Called when a new location is received.
     * Updates ServiceStateManager which notifies all observers (like MainActivity).
     */
    private fun onLocationReceived(location: Location) {
        locationCounter++
        lastLocation = location

        Log.d(TAG, "Location #$locationCounter: ${location.latitude}, ${location.longitude} " +
                "(accuracy: ${location.accuracy}m, speed: ${location.speed}m/s)")

        // Create LocationData and update StateFlow
        val locationData = LocationData.fromLocation(location, locationCounter)
        ServiceStateManager.updateLocation(locationData)

        // Update notification with current location info
        updateNotificationContent(
            "Location #$locationCounter\n" +
                    "Lat: %.6f, Lng: %.6f\n".format(location.latitude, location.longitude) +
                    "Speed: %.1f km/h".format(location.speed * 3.6f)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        // CRITICAL: Must call startForeground() within 5 seconds!
        startForegroundNotification()

        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_RESUME -> handleResume()
            ACTION_PAUSE -> handlePause()
            ACTION_STOP -> handleStop()
            else -> {
                // Service was restarted by system
                val desired = MyDB.getDesiredState(this)
                Log.d(TAG, "System restart - restoring state: $desired")
                when (desired) {
                    ServiceState.RUNNING -> handleStart()
                    ServiceState.PAUSED -> handlePause()
                    ServiceState.STOPPED -> handleStop()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        stopLocationUpdates()
        releaseWakeLock()
        ServiceStateManager.setServiceProcessRunning(false)
        ServiceStateManager.clearLocation()
        MCT7.get().cancelByTag(TAG)
        MCT7.get().cancelByTag("${TAG}_heartbeat")
        MyDB.setActualState(this, ServiceState.STOPPED)
    }

    // ===== State Handlers =====

    private fun handleStart() {
        if (currentState == ServiceState.RUNNING) return

        Log.d(TAG, "Starting work")
        currentState = ServiceState.RUNNING
        MyDB.setActualState(this, ServiceState.RUNNING)
        ServiceStateManager.updateState(ServiceState.RUNNING)

        // Wake lock is OPTIONAL for location tracking
        // Uncomment if you need CPU for additional processing:
        acquireWakeLock()

        startWork()
        updateNotificationContent("Starting location tracking...")
    }

    private fun handlePause() {
        if (currentState == ServiceState.PAUSED) return

        Log.d(TAG, "Pausing work")
        currentState = ServiceState.PAUSED
        MyDB.setActualState(this, ServiceState.PAUSED)
        ServiceStateManager.updateState(ServiceState.PAUSED)

        stopWork()
        releaseWakeLock()
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
        MyDB.setActualState(this, ServiceState.STOPPED)
        MyDB.clearHeartbeat(this)
        ServiceStateManager.updateState(ServiceState.STOPPED)
        ServiceStateManager.clearLocation()

        stopWork()
        stopHeartbeat()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ===== Wake Lock =====

    /**
     * Acquire a partial wake lock to keep CPU running.
     * NOTE: For location-only tracking, this is usually NOT needed.
     * The FusedLocationProvider keeps CPU awake during location callbacks.
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock != null) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            acquire()
        }
        Log.d(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    // ===== Location Updates =====

    private fun startWork() {
        stopWork() // Clear any existing

        startLocationUpdates()
        startHeartbeat()
    }

    /**
     * Start receiving location updates from FusedLocationProviderClient.
     */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Check permission first
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted!")
            return
        }

        // Build location request
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MS)
            setWaitForAccurateLocation(false)  // Don't wait, start immediately
        }.build()

        // Start updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()  // Callbacks on main thread
        )

        Log.d(TAG, "Location updates started (interval: ${LOCATION_INTERVAL_MS}ms)")
    }

    /**
     * Stop receiving location updates.
     */
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d(TAG, "Location updates stopped")
    }

    private fun stopWork() {
        stopLocationUpdates()
        MCT7.get().cancelByTag(TAG)
    }

    /**
     * Check if location permission is granted.
     */
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ===== Heartbeat =====

    private fun startHeartbeat() {
        stopHeartbeat()

        MCT7.get().cycle(
            MCT7.INFINITE,
            MyDB.HEARTBEAT_INTERVAL_MS,
            "${TAG}_heartbeat",
            object : MCT7.CycleTicker {
                override fun onTick(repeatsRemaining: Int) {
                    MyDB.updateHeartbeat(this@LocationService)
                }
            }
        )
    }

    private fun stopHeartbeat() {
        MCT7.get().cancelByTag("${TAG}_heartbeat")
    }

    // ===== Notification =====

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
            .setStyle(NotificationCompat.BigTextStyle()) // Allow multiline text

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notificationBuilder!!.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notificationBuilder!!.build())
        }
    }

    private fun updateNotificationContent(content: String) {
        notificationBuilder?.let { builder ->
            builder.setContentText(content)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(content))
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, builder.build())
        }
    }
}