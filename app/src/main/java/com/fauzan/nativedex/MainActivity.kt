package com.fauzan.nativedex

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.ActivityOptions

class MainActivity : AppCompatActivity() {

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

        val btnPermissions = Button(this).apply {
            text = "Enable Cursor Permissions"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                statusText.text = "Please enable 'NativeDex Injector' in Accessibility, and 'Draw over other apps'."
            }
        }
        layout.addView(btnPermissions)

        val btnStartCursor = Button(this).apply {
            text = "Start Cursor & Touchpad"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, TouchpadActivity::class.java))
            }
        }
        layout.addView(btnStartCursor)

        val btnLaunchDeX = Button(this).apply {
            text = "Launch Samsung DeX (HDMI Required)"
            isEnabled = false
            setOnClickListener {
                val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
                val extDisplay = displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
                if (extDisplay != null) {
                    try {
                        val dexIntent = Intent().apply {
                            setClassName("com.sec.android.app.launcher", "com.honeyspace.dexservice.SecondaryLauncher")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        }
                        val options = ActivityOptions.makeBasic()
                        options.launchDisplayId = extDisplay.displayId
                        startActivity(dexIntent, options.toBundle())
                        
                        // Also start our touchpad
                        startActivity(Intent(this@MainActivity, TouchpadActivity::class.java))
                    } catch (e: Exception) {
                        statusText.text = "Error launching DeX: ${e.message}"
                    }
                }
            }
        }
        layout.addView(btnLaunchDeX)

        val displayListText = TextView(this).apply {
            text = "Loading displays..."
            textSize = 14f
            setPadding(0, 32, 0, 0)
        }
        layout.addView(displayListText)

        val btnStartLocalDex = Button(this).apply {
            text = "Start Native LocalDex (Shizuku)"
            setTextColor(android.graphics.Color.BLUE)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LocalDexActivity::class.java))
            }
        }
        layout.addView(btnStartLocalDex)

        // Listen for display changes to toggle button
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { updateUI(btnLaunchDeX, displayListText) }
            override fun onDisplayRemoved(displayId: Int) { updateUI(btnLaunchDeX, displayListText) }
            override fun onDisplayChanged(displayId: Int) { updateUI(btnLaunchDeX, displayListText) }
        }
        displayManager.registerDisplayListener(displayListener, null)
        updateUI(btnLaunchDeX, displayListText)

        setContentView(layout)
    }

    private fun updateUI(button: Button, displayListText: TextView) {
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays
        
        val hasExternal = displays.any { it.displayId != Display.DEFAULT_DISPLAY }
        button.isEnabled = hasExternal
        if (hasExternal) {
            button.text = "Launch Samsung DeX Natively"
            button.setTextColor(android.graphics.Color.GREEN)
        } else {
            button.text = "Launch Samsung DeX (HDMI Required)"
            button.setTextColor(android.graphics.Color.GRAY)
        }
        
        val sb = StringBuilder("Available Displays:\n\n")
        displays.forEach { d ->
            sb.append("ID: ${d.displayId}\n")
            sb.append("Name: ${d.name}\n")
            sb.append("State: ${d.state}\n")
            sb.append("isValid: ${d.isValid}\n")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                sb.append("Mode: ${d.mode?.physicalWidth}x${d.mode?.physicalHeight}\n")
            }
            sb.append("---\n")
        }
        displayListText.text = sb.toString()
    }
}
