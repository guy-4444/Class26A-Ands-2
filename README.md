# Android Foreground Service Demo

A clean, educational example of implementing a foreground service in Android with proper permission handling and wake lock support.

## What is a Foreground Service?

A foreground service performs operations that are noticeable to the user (like playing music or tracking location). It must display a notification and has higher priority than regular background tasks.

## What is a Wake Lock?

A wake lock prevents the CPU from sleeping when the screen is off. Without it, your background work might not execute. Types:
- `PARTIAL_WAKE_LOCK` - CPU stays on, screen off (most common)
- `FULL_WAKE_LOCK` - CPU + screen on (deprecated, avoid)

**Use wake locks sparingly** - they drain battery!

## Project Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    PermissionsActivity (LAUNCHER)                │
│  - Checks and requests all permissions                          │
│  - Location, Background Location, Notifications                 │
│  - Navigates to MainActivity when done                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        MainActivity                              │
│  - UI buttons (Start/Stop/Pause/Resume)                         │
│  - Observes state changes via StateFlow                         │
│  - Battery optimization settings                                │
│  - Handles crash recovery when app opens                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      LocationService                             │
│  - The actual foreground service                                │
│  - Shows persistent notification                                │
│  - Manages wake lock for CPU                                    │
│  - Does the work (replace with your logic)                      │
│  - Sends heartbeats to prove it's alive                         │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐
│    MyDB      │    │ServiceStateManager│    │      MCT6        │
│  Persists    │    │  Reactive state   │    │  Timer utility   │
│  state to    │    │  (StateFlow)      │    │  (fixed delay)   │
│  SharedPrefs │    │                   │    │                  │
└──────────────┘    └──────────────────┘    └──────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     Supporting Components                        │
├──────────────────────────────────────────────────────────────────┤
│  BootReceiver         - Restarts service after device boot      │
│  ServiceMonitorWorker - Detects crashes, notifies user          │
│  BatteryOptimizationHelper - Manages battery exemption          │
└─────────────────────────────────────────────────────────────────┘
```

## Key Files

| File | Purpose |
|------|---------|
| `PermissionsActivity.kt` | **Launcher** - handles all permissions |
| `MainActivity.kt` | Service control UI (start/stop/pause) |
| `LocationService.kt` | The foreground service with wake lock |
| `ServiceState.kt` | Enum: STOPPED, RUNNING, PAUSED |
| `ServiceStateManager.kt` | Reactive state management |
| `MyDB.kt` | Persistence (SharedPreferences) |
| `MCT6.java` | Timer utility (uses `scheduleWithFixedDelay`) |
| `BootReceiver.kt` | Restart service after reboot |
| `ServiceMonitorWorker.kt` | Detect crashes, notify user |
| `BatteryOptimizationHelper.kt` | Battery optimization settings |

## Android 12+ Restrictions

Starting with Android 12 (API 31), you **cannot** start a foreground service from the background. Exceptions:
- User interaction (button tap in Activity)
- `BOOT_COMPLETED` broadcast
- App has battery optimization exemption

This is why we:
1. Only start service from Activity (user tap)
2. Use BootReceiver for restart after reboot
3. Show notification asking user to restart (can't auto-restart)

## Required Permissions

```xml
<!-- Foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

<!-- Location -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Wake lock (keeps CPU on) -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Boot restart -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Notifications (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Battery optimization dialog -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

## Wake Lock Usage

```kotlin
// In LocationService.kt:

// Acquire when starting work (no timeout = runs indefinitely)
private fun acquireWakeLock() {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "MyApp::MyWakeLock"
    ).apply {
        acquire() // No timeout - held until released
    }
}

// Release when stopping work
private fun releaseWakeLock() {
    wakeLock?.let {
        if (it.isHeld) it.release()
    }
    wakeLock = null
}
```

### Do You Actually Need a Wake Lock?

| Use Case | Wake Lock Needed? |
|----------|-------------------|
| Location tracking only | **NO** - system keeps CPU awake for location callbacks |
| Periodic uploads/sync | **YES** - CPU may sleep between your work |
| Continuous processing | **YES** - CPU may sleep if no active work |
| Playing audio | **NO** - MediaPlayer handles this internally |

**Important:** Always release wake locks when done! Holding unnecessarily drains battery.

## How to Add Your Own Work

In `LocationService.kt`, find the `startWork()` method:

```kotlin
private fun startWork() {
    MCT6.get().cycle(object : MCT6.CycleTicker {
        override fun secondly(repeatsRemaining: Int) {
            // ========================================
            // YOUR ACTUAL WORK GOES HERE
            // Example: Request location update
            // Example: Upload data to server
            // Example: Process sensor data
            // ========================================
        }
        override fun done() {}
    }, MCT6.CONTINUOUSLY_REPEATS, 1000, TAG)
}
```

## App Flow

```
App Launch
    │
    └─> PermissionsActivity
            │
            ├─> Check permissions
            │       │
            │       ├─> All granted? → Go to MainActivity
            │       │
            │       └─> Missing? → Request permissions
            │               │
            │               └─> Location → Background → Notifications
            │
            └─> MainActivity
                    │
                    ├─> Start button → LocationService.start()
                    │       │
                    │       ├─> startForeground() (notification)
                    │       ├─> acquireWakeLock()
                    │       └─> startWork()
                    │
                    ├─> Pause button → LocationService.pause()
                    │       │
                    │       ├─> stopWork()
                    │       └─> releaseWakeLock()
                    │
                    └─> Stop button → LocationService.stop()
                            │
                            ├─> stopWork()
                            ├─> releaseWakeLock()
                            └─> stopSelf()
```

## MCT6 Timer - scheduleWithFixedDelay

Uses `scheduleWithFixedDelay` instead of `scheduleAtFixedRate` to prevent task pile-up when Android process transitions from cached to uncached state.

```java
// scheduleAtFixedRate: Tasks can pile up ❌
// scheduleWithFixedDelay: Safe, waits for previous to complete ✓
executor.scheduleWithFixedDelay(
    task,
    0,              // initial delay
    periodMs,       // delay AFTER each execution completes
    TimeUnit.MILLISECONDS
);
```

## Tips for Students

1. **Always call startForeground() immediately** in `onStartCommand()` - you have only 5 seconds!

2. **Release wake locks** - forgetting to release drains battery

3. **Test with "Don't keep activities"** enabled in Developer Options

4. **Test boot restart** by rebooting the device

5. **Check Logcat** - all events logged with TAG "LocationService"

6. **Battery optimization** - encourage users to exempt your app for reliable operation