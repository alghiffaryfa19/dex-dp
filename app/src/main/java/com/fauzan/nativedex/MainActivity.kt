package com.fauzan.nativedex

import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.app.ActivityOptions
import android.util.Log

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NativeDexMain"
    }

    private lateinit var statusText: TextView
    private lateinit var btnLaunchDeX: Button
    private lateinit var chkDecorations: CheckBox
    private var displayListener: DisplayManager.DisplayListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        statusText = TextView(this).apply {
            text = "NativeDex Setup"
            textSize = 24f
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
        }
        layout.addView(statusText)

        // Display info
        val displayInfoText = TextView(this).apply {
            text = ""
            textSize = 12f
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setPadding(16, 16, 16, 16)
        }
        layout.addView(displayInfoText)

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

        // Decoration checkbox
        chkDecorations = CheckBox(this).apply {
            text = "Enable System Decorations (Wallpaper, Statusbar, Navbar)"
            isChecked = true
            setPadding(16, 8, 16, 8)
        }
        layout.addView(chkDecorations)

        btnLaunchDeX = Button(this).apply {
            text = "Launch Samsung DeX (HDMI Required)"
            isEnabled = false
            setOnClickListener {
                launchDeX()
            }
        }
        layout.addView(btnLaunchDeX)

        // Listen for display changes
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                runOnUiThread {
                    updateUI(displayInfoText)
                }
            }
            override fun onDisplayRemoved(displayId: Int) {
                runOnUiThread {
                    updateUI(displayInfoText)
                }
            }
            override fun onDisplayChanged(displayId: Int) {}
        }
        displayManager.registerDisplayListener(displayListener, null)
        updateUI(displayInfoText)

        setContentView(layout)
    }

    private fun launchDeX() {
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val extDisplay = displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (extDisplay == null) {
            statusText.text = "No external display found"
            return
        }

        val displayId = extDisplay.displayId
        Log.i(TAG, "Launching DeX on display $displayId (${extDisplay.name})")

        try {
            // If decorations enabled, try to enable system decorations on the display
            if (chkDecorations.isChecked) {
                enableSystemDecorations(displayId)
            }

            // Launch SecondaryLauncher with SECONDARY_HOME category (like localdex)
            val dexIntent = Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_SECONDARY_HOME)
                setClassName(
                    "com.sec.android.app.launcher",
                    "com.honeyspace.dexservice.SecondaryLauncher"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }

            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId
            startActivity(dexIntent, options.toBundle())

            Log.i(TAG, "SecondaryLauncher launched with SECONDARY_HOME on display $displayId")
            statusText.text = "DeX launched on display $displayId"

            // Also start touchpad
            startActivity(Intent(this@MainActivity, TouchpadActivity::class.java))

        } catch (e: Exception) {
            Log.e(TAG, "Error launching DeX", e)
            statusText.text = "Error launching DeX: ${e.message}"

            // Fallback: try without SECONDARY_HOME category
            try {
                Log.i(TAG, "Trying fallback launch without SECONDARY_HOME...")
                val fallbackIntent = Intent().apply {
                    action = Intent.ACTION_MAIN
                    setClassName(
                        "com.sec.android.app.launcher",
                        "com.honeyspace.dexservice.SecondaryLauncher"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                val options = ActivityOptions.makeBasic()
                options.launchDisplayId = extDisplay.displayId
                startActivity(fallbackIntent, options.toBundle())

                statusText.text = "DeX launched (fallback) on display $displayId"
                startActivity(Intent(this@MainActivity, TouchpadActivity::class.java))
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback launch also failed", fallbackEx)
                statusText.text = "All launch attempts failed:\n${fallbackEx.message}"
            }
        }
    }

    /**
     * Try to enable system decorations (wallpaper, statusbar, navbar) on the given display.
     * This uses the hidden DisplayManager API via reflection.
     * Requires WRITE_SECURE_SETTINGS permission (can be granted via ADB once).
     */
    private fun enableSystemDecorations(displayId: Int) {
        try {
            // Method 1: Try DisplayManager.setShouldShowSystemDecors (hidden API)
            val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
            val method = dm.javaClass.getMethod(
                "setShouldShowSystemDecors",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(dm, displayId, true)
            Log.i(TAG, "setShouldShowSystemDecors($displayId, true) succeeded")
        } catch (e: Exception) {
            Log.w(TAG, "setShouldShowSystemDecors failed (expected without WRITE_SECURE_SETTINGS): ${e.message}")

            // Method 2: Try via Settings.Global
            try {
                Settings.Global.putString(
                    contentResolver,
                    "display_decoration_enabled_$displayId",
                    "1"
                )
                Log.i(TAG, "Settings.Global decoration flag set for display $displayId")
            } catch (e2: Exception) {
                Log.w(TAG, "Settings.Global fallback also failed: ${e2.message}")
            }
        }

        // Method 3: Try setting IME policy to show on all displays
        try {
            Settings.Global.putInt(contentResolver, "force_desktop_mode_on_external_displays", 1)
            Log.i(TAG, "force_desktop_mode_on_external_displays set to 1")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set force_desktop_mode_on_external_displays: ${e.message}")
        }
    }

    private fun updateUI(displayInfoText: TextView) {
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val extDisplay = displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        val hasExternal = extDisplay != null

        btnLaunchDeX.isEnabled = hasExternal
        if (hasExternal && extDisplay != null) {
            btnLaunchDeX.text = "Launch Samsung DeX Natively"
            btnLaunchDeX.setTextColor(android.graphics.Color.GREEN)

            val mode = extDisplay.mode
            displayInfoText.text = buildString {
                append("External Display: ${extDisplay.name}\n")
                append("Display ID: ${extDisplay.displayId}\n")
                append("Resolution: ${mode.physicalWidth}x${mode.physicalHeight}\n")
                append("Refresh: ${mode.refreshRate}Hz")
            }
        } else {
            btnLaunchDeX.text = "Launch Samsung DeX (HDMI Required)"
            btnLaunchDeX.setTextColor(android.graphics.Color.GRAY)
            displayInfoText.text = "No external display connected"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        displayListener?.let {
            val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
            displayManager.unregisterDisplayListener(it)
        }
    }
}
