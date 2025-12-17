package com.guy.class26a_ands_2

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize timer helper
        MCT7.init()

        // Schedule periodic service monitor (checks if service crashed)
        // Note: On Android 12+, monitor can only NOTIFY user to restart,
        // it cannot auto-restart services from background.
        ServiceMonitorWorker.schedule(this)

        // Note: Crash recovery happens in MainActivity.onResume() because
        // Application context is "background" and cannot start foreground
        // services on Android 12+.
    }
}
