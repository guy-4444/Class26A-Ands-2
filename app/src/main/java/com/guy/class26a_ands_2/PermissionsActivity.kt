package com.guy.class26a_ands_2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.guy.class26a_ands_2.databinding.ActivityPermissionsBinding

/**
 * PermissionsActivity - Handles all permission requests before entering the app.
 *
 * This is the LAUNCHER activity. It checks permissions and then navigates to MainActivity.
 *
 * FLOW:
 * 1. Check if all permissions granted → Go to MainActivity
 * 2. If not, show explanation and request permissions
 * 3. After all permissions handled → Go to MainActivity
 *
 * REQUIRED PERMISSIONS:
 * - Location (Fine + Coarse) - for location tracking
 * - Background Location (Android 10+) - for tracking when app is closed
 * - Notifications (Android 13+) - for foreground service notification
 */
class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding

    // ===== Permission Launchers =====

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            // Location granted - check background location next
            checkBackgroundLocationPermission()
        } else {
            // Location denied - can't proceed without it
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show()
            updateStatus()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Background location recommended for full functionality", Toast.LENGTH_SHORT).show()
        }
        // Continue to notification permission
        checkNotificationPermission()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notifications recommended for service status", Toast.LENGTH_SHORT).show()
        }
        // All permissions checked - go to main
        goToMainActivity()
    }

    // ===== Lifecycle =====

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Check if all permissions already granted
        if (hasAllRequiredPermissions()) {
            goToMainActivity()
            return
        }

        setupUI()
        updateStatus()
    }

    private fun setupUI() {
        binding.btnGrantPermissions.setOnClickListener {
            startPermissionFlow()
        }

        binding.btnSkip.setOnClickListener {
            // Allow skipping, but warn user
            AlertDialog.Builder(this)
                .setTitle("Skip Permissions?")
                .setMessage("The app may not work correctly without permissions. Are you sure?")
                .setPositiveButton("Skip Anyway") { _, _ ->
                    goToMainActivity()
                }
                .setNegativeButton("Grant Permissions") { _, _ ->
                    startPermissionFlow()
                }
                .show()
        }
    }

    private fun updateStatus() {
        val locationGranted = hasLocationPermission()
        val backgroundGranted = hasBackgroundLocationPermission()
        val notificationGranted = hasNotificationPermission()

        binding.lblStatus.text = buildString {
            appendLine("Permission Status:")
            appendLine()
            appendLine("${if (locationGranted) "✓" else "✗"} Location")
            appendLine("${if (backgroundGranted) "✓" else "✗"} Background Location")
            appendLine("${if (notificationGranted) "✓" else "✗"} Notifications")
        }

        // Update button text based on state
        if (hasAllRequiredPermissions()) {
            binding.btnGrantPermissions.text = "Continue"
        } else {
            binding.btnGrantPermissions.text = "Grant Permissions"
        }
    }

    // ===== Permission Flow =====

    private fun startPermissionFlow() {
        when {
            hasAllRequiredPermissions() -> {
                goToMainActivity()
            }
            !hasLocationPermission() -> {
                requestLocationPermission()
            }
            !hasBackgroundLocationPermission() -> {
                checkBackgroundLocationPermission()
            }
            !hasNotificationPermission() -> {
                checkNotificationPermission()
            }
            else -> {
                goToMainActivity()
            }
        }
    }

    private fun requestLocationPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            AlertDialog.Builder(this)
                .setTitle("Location Permission")
                .setMessage("This app needs location access to track your position for the service.")
                .setPositiveButton("Grant") { _, _ ->
                    launchLocationRequest()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            launchLocationRequest()
        }
    }

    private fun launchLocationRequest() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasBackgroundLocationPermission()) {
                checkNotificationPermission()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Background Location")
                    .setMessage("For the service to work when the app is closed, select \"Allow all the time\" on the next screen.")
                    .setPositiveButton("Continue") { _, _ ->
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        checkNotificationPermission()
                    }
                    .show()
            }
        } else {
            checkNotificationPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (hasNotificationPermission()) {
                goToMainActivity()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            goToMainActivity()
        }
    }

    // ===== Permission Checks =====

    private fun hasAllRequiredPermissions(): Boolean {
        return hasLocationPermission() // Minimum required
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ===== Navigation =====

    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish() // Don't keep this activity in back stack
    }
}
