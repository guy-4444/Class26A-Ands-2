package com.guy.class26a_ands_2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.guy.class26a_ands_2.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.progressIndicator.visibility = View.VISIBLE

        binding.btnAction.setOnClickListener {
            actionClicked()
        }

        binding.btnInfo.setOnClickListener {
            updateUI()
        }

        updateUI()
    }

    private fun actionClicked() {
        if (LocationService.isMyServiceRunning(this)) {
            MyDB.saveState(this, false)
            commandToService(LocationService.ACTION_STOP_FOREGROUND_SERVICE)
        } else {
            MyDB.saveState(this, true)
            commandToService(LocationService.ACTION_START_FOREGROUND_SERVICE)
        }

        updateUI()
    }

    private fun updateUI() {
        if (LocationService.isMyServiceRunning(this)) {
            binding.btnAction.text = "Stop"
            binding.progressIndicator.visibility = View.VISIBLE
        } else {
            binding.btnAction.text = "Start"
            binding.progressIndicator.visibility = View.GONE
        }

        val statusStr = "isNeedToRun: " + MyDB.isNeedToRun(this)
        binding.lblInfo.text = statusStr
    }

    private fun commandToService(command: String) {
        Log.d("pttt", "commandToService: $command")
        val intent = Intent(this, LocationService::class.java)
        intent.action = command
        startForegroundService(intent)
    }
}



