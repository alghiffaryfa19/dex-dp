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
                if (!checkPermissions()) return@setOnClickListener
                scope.launch {
                    try {
                        val connected = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            val manager = Adb.createManager(this@MainActivity)
                            manager.autoConnect(this@MainActivity, 5_000)
                        }
                        if (connected) {
                            statusText.text = "ADB already connected!"
                        } else {
                            startService(Intent(this@MainActivity, PairingInputService::class.java))
                            statusText.text = "Follow pairing instructions in notification"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // libadb throws when pairing is required
                        startService(Intent(this@MainActivity, PairingInputService::class.java))
                        statusText.text = "Follow pairing instructions in notification"
                    }
                }
            }
        }
        layout.addView(btnAdbPair)

        val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val displayInputLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }

        val displayInput = android.widget.EditText(this).apply {
            hint = "Display ID (e.g., 0, 1, 2)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        displayInputLayout.addView(displayInput)

        val btnGetDisplays = Button(this).apply {
            text = "List Displays"
            setOnClickListener {
                val displays = displayManager.displays
                val displayInfo = displays.joinToString("\n") { "ID: ${it.displayId} - ${it.name}" }
                statusText.text = "Available Displays:\n$displayInfo"
            }
        }
        displayInputLayout.addView(btnGetDisplays)
        layout.addView(displayInputLayout)

        val btnDeXPrimary = Button(this).apply {
            text = "Test DeX on Selected Display"
            setOnClickListener {
                val displayIdStr = displayInput.text.toString()
                if (displayIdStr.isEmpty()) {
                    statusText.text = "Please enter a Display ID."
                    return@setOnClickListener
                }
                val displayId = displayIdStr.toIntOrNull() ?: 0

                scope.launch {
                    try {
                        val connected = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            val manager = Adb.createManager(this@MainActivity)
                            if (manager.autoConnect(this@MainActivity, 5_000)) {
                                val startDexCommand = "am start -n com.sec.android.app.launcher/com.honeyspace.dexservice.SecondaryLauncher --display $displayId"
                                Adb.runShell(manager, startDexCommand)
                                true
                            } else {
                                false
                            }
                        }
                        
                        if (connected) {
                            statusText.text = "DeX command sent to Display $displayId!"
                        } else {
                            statusText.text = "Please connect ADB first."
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        statusText.text = "Failed: ${e.message}"
                    }
                }
            }
        }
        layout.addView(btnDeXPrimary)

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

    companion object {
        const val EXTRA_FROM_PAIRING = "com.fauzan.nativedex.FROM_PAIRING"
    }
}
