package com.guy.class26a_ands_2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Helper for battery optimization exemption.
 *
 * WHY IS THIS IMPORTANT?
 * - Android aggressively kills background apps to save battery
 * - If our app is "battery optimized", service may be killed unexpectedly
 * - Requesting exemption helps keep service running reliably
 *
 * HOW TO USE:
 * 1. Check: isIgnoringBatteryOptimizations()
 * 2. If false, show dialog explaining why it's needed
 * 3. Call: requestIgnoreBatteryOptimizations() to open system settings
 * 4. User must manually enable the exemption
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
     * Open system dialog to request battery optimization exemption.
     * User must manually toggle the setting.
     *
     * Note: Some manufacturers (Xiaomi, Huawei, etc.) have additional
     * battery settings that this won't cover.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    /**
     * Open battery optimization settings for all apps.
     * Use as fallback if direct request doesn't work on some devices.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    }
}
