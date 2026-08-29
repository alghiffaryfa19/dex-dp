package com.fauzan.nativedex

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        
        val statusText = TextView(this).apply {
            text = "NativeDex Setup"
            textSize = 24f
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
        }
        layout.addView(statusText)

        val btnStart = Button(this).apply {
            text = "Start NativeDex Monitor"
            setOnClickListener {
                if (checkPermissions()) {
                    startMonitorService()
                    statusText.text = "Monitor Service is running.\nConnect HDMI to trigger Desktop mode."
                }
            }
        }
        layout.addView(btnStart)
        
        val btnAdbPair = Button(this).apply {
            text = "Pair Wireless Debugging (ADB)"
            setOnClickListener {
                scope.launch {
                    try {
                        val manager = Adb.createManager(this@MainActivity)
                        if (manager.autoConnect(this@MainActivity, 5_000)) {
                            statusText.text = "ADB already connected!"
                        } else {
                            WirelessDebugging.askToPair(this@MainActivity)
                            statusText.text = "Follow pairing instructions in notification"
                        }
                    } catch (e: Exception) {
                        statusText.text = "Error connecting ADB: ${e.message}"
                    }
                }
            }
        }
        layout.addView(btnAdbPair)

        setContentView(layout)
    }
    
    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return false
            }
        }
        return true
    }

    private fun startMonitorService() {
        val intent = Intent(this, DisplayMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
