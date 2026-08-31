package com.fauzan.nativedex

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
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

        // Permission status & grant button
        val permStatusText = TextView(this).apply {
            textSize = 11f
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setPadding(16, 4, 16, 4)
        }
        layout.addView(permStatusText)

        val btnGrantPerm = Button(this).apply {
            text = "Grant WRITE_SECURE_SETTINGS (copy ADB command)"
            textSize = 11f
            setOnClickListener {
                val cmd = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ADB command", cmd))
                Toast.makeText(this@MainActivity, "Command copied! Run once via ADB, then restart app.", Toast.LENGTH_LONG).show()
            }
        }
        layout.addView(btnGrantPerm)

        // Update permission status
        updatePermStatus(permStatusText, btnGrantPerm)

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

    private fun hasWriteSecureSettings(): Boolean {
        return checkCallingOrSelfPermission(
            "android.permission.WRITE_SECURE_SETTINGS"
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updatePermStatus(statusView: TextView, grantBtn: Button) {
        if (hasWriteSecureSettings()) {
            statusView.text = "✅ WRITE_SECURE_SETTINGS granted — decorations will work!"
            statusView.setTextColor(android.graphics.Color.GREEN)
            grantBtn.visibility = android.view.View.GONE
        } else {
            statusView.text = "⚠️ WRITE_SECURE_SETTINGS not granted — wallpaper/decorations may not appear"
            statusView.setTextColor(android.graphics.Color.parseColor("#FF9800"))
            grantBtn.visibility = android.view.View.VISIBLE
        }
    }

    /**
     * Try to enable system decorations (wallpaper, statusbar, navbar) on the given display.
     * Uses multiple approaches in order of reliability.
     */
    private fun enableSystemDecorations(displayId: Int) {
        val hasPermission = hasWriteSecureSettings()
        Log.i(TAG, "enableSystemDecorations(display=$displayId) hasWriteSecureSettings=$hasPermission")

        // Method 1: setShouldShowSystemDecors (requires WRITE_SECURE_SETTINGS)
        if (hasPermission) {
            try {
                val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
                val method = dm.javaClass.getMethod(
                    "setShouldShowSystemDecors",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
                method.invoke(dm, displayId, true)
                Log.i(TAG, "✅ setShouldShowSystemDecors($displayId, true) succeeded!")
            } catch (e: Exception) {
                Log.w(TAG, "setShouldShowSystemDecors failed: ${e.message}")
            }

            // Also enable freeform & desktop mode
            try {
                Settings.Global.putInt(contentResolver, "force_desktop_mode_on_external_displays", 1)
                Log.i(TAG, "✅ force_desktop_mode_on_external_displays = 1")
            } catch (e: Exception) {
                Log.w(TAG, "force_desktop_mode_on_external_displays failed: ${e.message}")
            }

            try {
                Settings.Global.putInt(contentResolver, "enable_freeform_support", 1)
                Log.i(TAG, "✅ enable_freeform_support = 1")
            } catch (e: Exception) {
                Log.w(TAG, "enable_freeform_support failed: ${e.message}")
            }
        } else {
            Log.w(TAG, "⚠️ WRITE_SECURE_SETTINGS not granted. Run: adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
        }

        // Method 2: Samsung-specific — try to activate DeX mode via SemDesktopModeManager
        trySamsungDesktopMode(displayId)

        // Method 3: Samsung-specific — send DeX mode broadcast
        trySamsungDexBroadcast(displayId)
    }

    /**
     * Try Samsung SemDesktopModeManager to properly initialize DeX
     * (which handles wallpaper/decorations internally).
     */
    private fun trySamsungDesktopMode(displayId: Int) {
        // Attempt 1: SemDesktopModeManager
        try {
            val clazz = Class.forName("com.samsung.android.desktopmode.SemDesktopModeManager")
            val constructor = clazz.getDeclaredConstructor(Context::class.java)
            constructor.isAccessible = true
            val manager = constructor.newInstance(this)

            // Try setDesktopModeEnabled
            try {
                val enableMethod = clazz.getMethod("setDesktopModeEnabled", Boolean::class.javaPrimitiveType)
                enableMethod.invoke(manager, true)
                Log.i(TAG, "✅ SemDesktopModeManager.setDesktopModeEnabled(true) succeeded!")
            } catch (e: Exception) {
                Log.w(TAG, "setDesktopModeEnabled failed: ${e.message}")
            }

            // Try enableDesktopMode with displayId
            try {
                val enableOnDisplayMethod = clazz.getMethod(
                    "enableDesktopMode",
                    Int::class.javaPrimitiveType
                )
                enableOnDisplayMethod.invoke(manager, displayId)
                Log.i(TAG, "✅ SemDesktopModeManager.enableDesktopMode($displayId) succeeded!")
            } catch (e: Exception) {
                Log.d(TAG, "enableDesktopMode(displayId) not available: ${e.message}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "SemDesktopModeManager not available (non-Samsung or different API): ${e.message}")
        }

        // Attempt 2: SemWindowManager for desktop mode
        try {
            val clazz = Class.forName("com.samsung.android.view.SemWindowManager")
            val getInstanceMethod = clazz.getMethod("getInstance")
            val instance = getInstanceMethod.invoke(null)

            val setDesktopMode = clazz.getMethod(
                "setDesktopModeEnabled",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            setDesktopMode.invoke(instance, displayId, true)
            Log.i(TAG, "✅ SemWindowManager.setDesktopModeEnabled($displayId, true) succeeded!")
        } catch (e: Exception) {
            Log.d(TAG, "SemWindowManager not available: ${e.message}")
        }
    }

    /**
     * Try Samsung-specific broadcasts to trigger DeX mode activation.
     */
    private fun trySamsungDexBroadcast(displayId: Int) {
        val broadcastActions = listOf(
            "com.samsung.android.desktopmode.action.DESKTOP_MODE_CHANGED",
            "com.samsung.android.knox.intent.action.DESKTOP_MODE_ENABLED",
            "com.sec.android.desktopmode.action.DEX_CONNECTED"
        )

        for (action in broadcastActions) {
            try {
                val intent = Intent(action).apply {
                    putExtra("enabled", true)
                    putExtra("displayId", displayId)
                    putExtra("android.intent.extra.DISPLAY_ID", displayId)
                }
                sendBroadcast(intent)
                Log.i(TAG, "Sent broadcast: $action")
            } catch (e: Exception) {
                Log.d(TAG, "Broadcast $action failed: ${e.message}")
            }
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
