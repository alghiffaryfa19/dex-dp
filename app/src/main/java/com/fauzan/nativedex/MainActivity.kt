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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.LinearLayout
import android.view.Gravity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        scrollView.addView(layout)
        
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

        val customLaunchTitle = TextView(this).apply {
            text = "Custom Activity Launcher"
            textSize = 20f
            setPadding(0, 64, 0, 16)
        }
        layout.addView(customLaunchTitle)
        
        val classInput = EditText(this).apply {
            hint = "Package/Class (e.g. com.android.settings/.Settings)"
        }
        layout.addView(classInput)
        
        val displaySpinner = Spinner(this)
        layout.addView(displaySpinner)
        
        val displayAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item)
        displayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        displaySpinner.adapter = displayAdapter
        
        val btnLaunchCustom = Button(this).apply {
            text = "Launch Custom Activity"
            setOnClickListener {
                val selectedDisplayString = displaySpinner.selectedItem as? String
                val displayId = selectedDisplayString?.substringBefore(":")?.substringAfter("Display ")?.trim()?.toIntOrNull()
                val className = classInput.text.toString().trim()
                
                if (displayId != null && className.isNotEmpty()) {
                    try {
                        var pkg = ""
                        var cls = className
                        if (className.contains("/")) {
                            pkg = className.substringBefore("/")
                            cls = className.substringAfter("/")
                            if (cls.startsWith(".")) {
                                cls = pkg + cls
                            }
                        } else {
                            pkg = className.substringBeforeLast(".")
                        }
                        
                        val intent = Intent().apply {
                            setClassName(pkg, cls)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        }
                        val options = ActivityOptions.makeBasic()
                        options.launchDisplayId = displayId
                        startActivity(intent, options.toBundle())
                    } catch (e: Exception) {
                        statusText.text = "Error launching: ${e.message}"
                    }
                }
            }
        }
        layout.addView(btnLaunchCustom)

        // Listen for display changes to toggle button
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { 
                updateButton(btnLaunchDeX)
                updateDisplays(displayManager, displayAdapter)
            }
            override fun onDisplayRemoved(displayId: Int) { 
                updateButton(btnLaunchDeX)
                updateDisplays(displayManager, displayAdapter)
            }
            override fun onDisplayChanged(displayId: Int) {
                updateDisplays(displayManager, displayAdapter)
            }
        }
        displayManager.registerDisplayListener(displayListener, null)
        updateButton(btnLaunchDeX)
        updateDisplays(displayManager, displayAdapter)

        setContentView(scrollView)
    }

    private fun updateDisplays(displayManager: DisplayManager, adapter: ArrayAdapter<String>) {
        adapter.clear()
        displayManager.displays.forEach { display ->
            adapter.add("Display ${display.displayId}: ${display.name}")
        }
        adapter.notifyDataSetChanged()
    }

    private fun updateButton(button: Button) {
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val hasExternal = displayManager.displays.any { it.displayId != Display.DEFAULT_DISPLAY }
        button.isEnabled = hasExternal
        if (hasExternal) {
            button.text = "Launch Samsung DeX Natively"
            button.setTextColor(android.graphics.Color.GREEN)
        } else {
            button.text = "Launch Samsung DeX (HDMI Required)"
            button.setTextColor(android.graphics.Color.GRAY)
        }
    }
}
