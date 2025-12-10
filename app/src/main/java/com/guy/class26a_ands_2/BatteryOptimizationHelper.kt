package com.guy.class26a_ands_2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Helper for battery optimization exemption.
 *
 * If user disables battery optimization for the app, we CAN start foreground
 * services from the background even on Android 12+.
 *
 * This is the most reliable way to enable auto-restart after crash.
 */
object BatteryOptimizationHelper {

    /**
     * Check if app is exempted from battery optimization.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Open system settings to request battery optimization exemption.
     * User must manually toggle the setting.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    /**
     * Open battery optimization settings for all apps.
     * Use this as fallback if direct request doesn't work.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    }
}