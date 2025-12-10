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

        // Handle restart action from notification
        handleIntent()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent()
    }

    private fun handleIntent() {
        if (intent?.action == ServiceMonitorWorker.ACTION_RESTART_SERVICE) {
            // User tapped restart notification - we're now in foreground, so start service
            val desiredState = MyDB.getDesiredState(this)
            if (desiredState == ServiceState.RUNNING || desiredState == ServiceState.PAUSED) {
                Toast.makeText(this, "Restarting service...", Toast.LENGTH_SHORT).show()
                LocationService.start(this)
                if (desiredState == ServiceState.PAUSED) {
                    MCT6.get().single({ LocationService.pause(this) }, 500, "restart_pause")
                }
            }
            // Clear the action
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

    private fun setupButtons() {
        // Main toggle button: Start/Stop
        binding.btnToggle.setOnClickListener {
            val currentState = ServiceStateManager.state.value
            when (currentState) {
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
            val currentState = ServiceStateManager.state.value
            when (currentState) {
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

    private fun showBatteryOptimizationDialog() {
        val isExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)

        if (isExempt) {
            AlertDialog.Builder(this)
                .setTitle("Battery Optimization")
                .setMessage("✓ App is already exempted from battery optimization.\n\nAuto-restart after crash is enabled.")
                .setPositiveButton("OK", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Enable Auto-Restart")
                .setMessage("To allow the app to automatically restart the service after a crash, you need to disable battery optimization.\n\nThis helps keep the service running reliably.")
                .setPositiveButton("Open Settings") { _, _ ->
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceStateManager.state.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: ServiceState) {
        when (state) {
            ServiceState.STOPPED -> {
                binding.btnToggle.setImageResource(R.drawable.ic_media_play)
                binding.btnPauseResume.visibility = View.GONE
                binding.progressIndicator.visibility = View.GONE
            }
            ServiceState.RUNNING -> {
                binding.btnToggle.setImageResource(R.drawable.ic_media_stop)
                binding.btnPauseResume.visibility = View.VISIBLE
                binding.btnPauseResume.setImageResource(R.drawable.ic_media_pause)
                binding.progressIndicator.visibility = View.VISIBLE
            }
            ServiceState.PAUSED -> {
                binding.btnToggle.setImageResource(R.drawable.ic_media_stop)
                binding.btnPauseResume.visibility = View.VISIBLE
                binding.btnPauseResume.setImageResource(R.drawable.ic_media_play)
                binding.progressIndicator.visibility = View.GONE
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
        if (batteryOptExempt) {
            binding.btnBatteryOptimization.text = "✓ Auto-restart enabled"
        } else {
            binding.btnBatteryOptimization.text = "Enable auto-restart"
        }
    }
}