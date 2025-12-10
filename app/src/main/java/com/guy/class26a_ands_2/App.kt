package com.guy.class26a_ands_2

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize timer helper
        MCT6.initHelper()

        // Schedule periodic service monitor
        // Note: Monitor can only NOTIFY user to restart, not auto-restart (Android 12+ restriction)
        ServiceMonitorWorker.schedule(this)

        // Note: We do NOT attempt crash recovery here because Application context
        // is considered "background" and we cannot start foreground services from here
        // on Android 12+. Recovery happens in MainActivity.onResume() instead.
    }
}