package com.guy.class26a_ands_2

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.guy.class26a_ands_2.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * MainActivity - Service control interface.
 *
 * This activity handles:
 * - Start/Stop service
 * - Pause/Resume service
 * - Display service status
 * - Display location data (speed, coordinates, etc.)
 * - Battery optimization settings
 *
 * Permission handling is done in PermissionsActivity (the launcher).
 *
 * DATA FLOW:
 * LocationService → ServiceStateManager.locationData → MainActivity observes → UI updates
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupButtons()
        observeState()
        observeLocation()
        handleIntent()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent()
    }

    /**
     * Handle restart action from notification.
     */
    private fun handleIntent() {
        if (intent?.action == ServiceMonitorWorker.ACTION_RESTART_SERVICE) {
            val desiredState = MyDB.getDesiredState(this)
            if (desiredState == ServiceState.RUNNING || desiredState == ServiceState.PAUSED) {
                Toast.makeText(this, "Restarting service...", Toast.LENGTH_SHORT).show()
                LocationService.start(this)
                if (desiredState == ServiceState.PAUSED) {
                    MCT7.get().delay(500, "restart_pause") {
                        LocationService.pause(this)
                    }
                }
            }
            intent?.action = null
        }
    }

    override fun onResume() {
        super.onResume()

        // Check for crash recovery - we're in foreground so we CAN start service
        val recovered = ServiceStateManager.checkAndRecoverIfNeeded(this)
        if (recovered) {
            Toast.makeText(this, "Service recovered", Toast.LENGTH_SHORT).show()
        }

        updateUI(ServiceStateManager.state.value)
    }

    // ===== Button Setup =====

    private fun setupButtons() {
        // Main toggle button: Start/Stop
        binding.btnToggle.setOnClickListener {
            when (ServiceStateManager.state.value) {
                ServiceState.STOPPED -> {
                    MyDB.setDesiredState(this, ServiceState.RUNNING)
                    LocationService.start(this)
                }
                ServiceState.RUNNING, ServiceState.PAUSED -> {
                    MyDB.setDesiredState(this, ServiceState.STOPPED)
                    LocationService.stop(this)
                }
            }
        }

        // Pause/Resume button
        binding.btnPauseResume.setOnClickListener {
            when (ServiceStateManager.state.value) {
                ServiceState.RUNNING -> {
                    MyDB.setDesiredState(this, ServiceState.PAUSED)
                    LocationService.pause(this)
                }
                ServiceState.PAUSED -> {
                    MyDB.setDesiredState(this, ServiceState.RUNNING)
                    LocationService.resume(this)
                }
                else -> { }
            }
        }

        binding.btnRefresh.setOnClickListener {
            updateUI(ServiceStateManager.state.value)
        }

        binding.btnBatteryOptimization.setOnClickListener {
            showBatteryOptimizationDialog()
        }
    }

    // ===== Battery Optimization =====

    private fun showBatteryOptimizationDialog() {
        val isExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)

        if (isExempt) {
            AlertDialog.Builder(this)
                .setTitle("Battery Optimization")
                .setMessage("✓ App is exempted from battery optimization.\n\nAuto-restart after crash is enabled.")
                .setPositiveButton("OK", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Enable Auto-Restart")
                .setMessage("To allow the app to automatically restart the service after a crash, disable battery optimization.\n\nThis helps keep the service running reliably.")
                .setPositiveButton("Open Settings") { _, _ ->
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ===== State Observation =====

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceStateManager.state.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    // ===== Location Observation =====

    private fun observeLocation() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceStateManager.locationData.collect { locationData ->
                    updateLocationUI(locationData)
                }
            }
        }
    }

    /**
     * Update UI with location data.
     */
    private fun updateLocationUI(data: LocationData?) {
        if (data == null) {
            // No location data - hide or show default values
            binding.lblSpeed.text = "-- km/h"
            binding.lblCoordinates.text = "Lat: --\nLng: --"
            binding.lblLocationInfo.text = "Waiting for location..."
            return
        }

        // Update speed display
        binding.lblSpeed.text = "%.1f km/h".format(data.speedKmh)

        // Update coordinates
        binding.lblCoordinates.text = "Lat: %.6f\nLng: %.6f".format(
            data.latitude,
            data.longitude
        )

        // Update additional info
        binding.lblLocationInfo.text = buildString {
            appendLine("Location #${data.locationCount}")
            appendLine("Accuracy: %.1f m".format(data.accuracy))
            appendLine("Altitude: %.1f m".format(data.altitude))
            appendLine("Bearing: %.0f°".format(data.bearing))
        }
    }

    private fun updateUI(state: ServiceState) {
        when (state) {
            ServiceState.STOPPED -> {
                binding.btnToggle.setImageResource(R.drawable.ic_media_play)
                binding.btnPauseResume.visibility = View.GONE
                binding.progressIndicator.visibility = View.GONE
                binding.locationCard.visibility = View.GONE
            }
            ServiceState.RUNNING -> {
                binding.btnToggle.setImageResource(R.drawable.ic_media_stop)
                binding.btnPauseResume.visibility = View.VISIBLE
                binding.btnPauseResume.setImageResource(R.drawable.ic_media_pause)
                binding.progressIndicator.visibility = View.VISIBLE
                binding.locationCard.visibility = View.VISIBLE
            }
            ServiceState.PAUSED -> {
                binding.btnToggle.setImageResource(R.drawable.ic_media_stop)
                binding.btnPauseResume.visibility = View.VISIBLE
                binding.btnPauseResume.setImageResource(R.drawable.ic_media_play)
                binding.progressIndicator.visibility = View.GONE
                binding.locationCard.visibility = View.VISIBLE
            }
        }

        // Update status info
        val desiredState = MyDB.getDesiredState(this)
        val actualState = MyDB.getActualState(this)
        val isAlive = MyDB.isServiceAlive(this)
        val isProcessRunning = ServiceStateManager.isServiceProcessRunning.value
        val batteryOptExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)

        binding.lblInfo.text = buildString {
            appendLine("State: $state")
            appendLine("Desired: $desiredState")
            appendLine("Actual: $actualState")
            appendLine("Process Running: $isProcessRunning")
            appendLine("Heartbeat Active: $isAlive")
            appendLine("Auto-restart enabled: $batteryOptExempt")
        }

        // Update battery optimization button appearance
        binding.btnBatteryOptimization.text = if (batteryOptExempt) {
            "✓ Auto-restart enabled"
        } else {
            "Enable auto-restart"
        }
    }
}