package com.fauzan.nativedex

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Renders a VirtualDisplay on the phone's built-in screen using SurfaceView.
 * Launches Samsung's SecondaryLauncher (DeX) on the virtual display.
 * Touch input is forwarded to the virtual display via the Accessibility Service.
 *
 * This works WITHOUT ADB, Shizuku, or root.
 * Limitation: The virtual display is PRIVATE, so apps launched FROM the
 * DeX launcher may be blocked. If that happens, the user needs Shizuku.
 */
class DexViewerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DexViewer"

        // Virtual display dimensions (landscape DeX)
        const val VDISPLAY_WIDTH = 1920
        const val VDISPLAY_HEIGHT = 1080
        const val VDISPLAY_DPI = 240

        // Virtual display flags (public API — no elevated permissions needed)
        private const val FLAG_OWN_CONTENT_ONLY = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        private const val FLAG_PRESENTATION = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

        // Hidden flags — will be stripped by the system if we don't have shell UID,
        // but we try anyway in case Samsung allows them
        private const val FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 shl 9
        private const val FLAG_TRUSTED = 1 shl 10
        private const val FLAG_PUBLIC = 1 shl 0
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var surfaceView: SurfaceView? = null
    private var statusOverlay: TextView? = null
    private var virtualDisplayId: Int = -1

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen immersive
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        // SurfaceView — virtual display renders here
        surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(surfaceView)

        // Status overlay
        statusOverlay = TextView(this).apply {
            text = "Initializing virtual display..."
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setBackgroundColor(0x88000000.toInt())
            setPadding(24, 24, 24, 24)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        rootLayout.addView(statusOverlay)

        setContentView(rootLayout)

        // Setup surface callbacks
        surfaceView!!.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.i(TAG, "Surface created, creating virtual display...")
                createVirtualDisplay(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.i(TAG, "Surface changed: ${width}x${height}")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.i(TAG, "Surface destroyed, releasing virtual display")
                releaseVirtualDisplay()
            }
        })

        // Direct touch → forward to virtual display via accessibility service
        surfaceView!!.setOnTouchListener { view, event ->
            handleDirectTouch(view, event)
        }
    }

    private fun createVirtualDisplay(holder: SurfaceHolder) {
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager

        // Try with all flags (decoration + trusted + public + own_content)
        // The system will strip flags it doesn't allow, but we try anyway
        val aggressiveFlags = FLAG_OWN_CONTENT_ONLY or
            FLAG_PRESENTATION or
            FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS or
            FLAG_TRUSTED or
            FLAG_PUBLIC

        var vd: VirtualDisplay? = null

        // Attempt 1: Try with all flags
        try {
            vd = dm.createVirtualDisplay(
                "NativeDex-Desktop",
                VDISPLAY_WIDTH,
                VDISPLAY_HEIGHT,
                VDISPLAY_DPI,
                holder.surface,
                aggressiveFlags
            )
            Log.i(TAG, "Virtual display created with aggressive flags")
        } catch (e: Exception) {
            Log.w(TAG, "Aggressive flags failed: ${e.message}")
        }

        // Attempt 2: Try with just presentation + own_content
        if (vd == null) {
            try {
                vd = dm.createVirtualDisplay(
                    "NativeDex-Desktop",
                    VDISPLAY_WIDTH,
                    VDISPLAY_HEIGHT,
                    VDISPLAY_DPI,
                    holder.surface,
                    FLAG_OWN_CONTENT_ONLY or FLAG_PRESENTATION
                )
                Log.i(TAG, "Virtual display created with presentation flags")
            } catch (e: Exception) {
                Log.w(TAG, "Presentation flags also failed: ${e.message}")
            }
        }

        // Attempt 3: Bare minimum
        if (vd == null) {
            try {
                vd = dm.createVirtualDisplay(
                    "NativeDex-Desktop",
                    VDISPLAY_WIDTH,
                    VDISPLAY_HEIGHT,
                    VDISPLAY_DPI,
                    holder.surface,
                    FLAG_OWN_CONTENT_ONLY
                )
                Log.i(TAG, "Virtual display created with minimum flags")
            } catch (e: Exception) {
                Log.e(TAG, "All virtual display creation attempts failed", e)
                runOnUiThread {
                    statusOverlay?.text = "Failed to create virtual display:\n${e.message}"
                }
                return
            }
        }

        virtualDisplay = vd
        virtualDisplayId = vd!!.display.displayId
        Log.i(TAG, "Virtual display ID: $virtualDisplayId")

        // If we have WRITE_SECURE_SETTINGS, try to enable decorations on the virtual display
        if (hasWriteSecureSettings()) {
            enableDecorationsOnDisplay(virtualDisplayId)
        }

        runOnUiThread {
            statusOverlay?.text = "Virtual Display $virtualDisplayId created. Launching DeX..."
            statusOverlay?.postDelayed({
                launchSecondaryLauncher(virtualDisplayId)
            }, 500)
        }
    }

    private fun enableDecorationsOnDisplay(displayId: Int) {
        try {
            val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
            val method = dm.javaClass.getMethod(
                "setShouldShowSystemDecors",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(dm, displayId, true)
            Log.i(TAG, "✅ setShouldShowSystemDecors($displayId, true)")
        } catch (e: Exception) {
            Log.w(TAG, "setShouldShowSystemDecors not available: ${e.message}")
        }

        try {
            Settings.Global.putInt(contentResolver, "force_desktop_mode_on_external_displays", 1)
            Settings.Global.putInt(contentResolver, "enable_freeform_support", 1)
            Log.i(TAG, "✅ Desktop mode + freeform settings applied")
        } catch (e: Exception) {
            Log.w(TAG, "Settings write failed: ${e.message}")
        }

        // Try setShouldShowIme
        try {
            val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
            val method = dm.javaClass.getMethod(
                "setShouldShowIme",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(dm, displayId, true)
            Log.i(TAG, "✅ setShouldShowIme($displayId, true)")
        } catch (e: Exception) {
            Log.w(TAG, "setShouldShowIme not available: ${e.message}")
        }
    }

    private fun launchSecondaryLauncher(displayId: Int) {
        try {
            // Launch with SECONDARY_HOME category (like localdex)
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

            Log.i(TAG, "✅ SecondaryLauncher launched on virtual display $displayId")

            // Attach accessibility service cursor to the virtual display
            val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(displayId)
            if (display != null) {
                NativeDexAccessibilityService.instance?.attachToDisplay(display)
            }

            statusOverlay?.visibility = View.GONE

        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch SecondaryLauncher", e)

            // Fallback: try without SECONDARY_HOME
            try {
                val fallbackIntent = Intent().apply {
                    action = Intent.ACTION_MAIN
                    setClassName(
                        "com.sec.android.app.launcher",
                        "com.honeyspace.dexservice.SecondaryLauncher"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                val options = ActivityOptions.makeBasic()
                options.launchDisplayId = displayId
                startActivity(fallbackIntent, options.toBundle())

                Log.i(TAG, "✅ SecondaryLauncher launched (fallback) on display $displayId")
                statusOverlay?.visibility = View.GONE
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "All launch attempts failed", fallbackEx)
                statusOverlay?.text = buildString {
                    append("Failed to launch DeX:\n")
                    append("${fallbackEx.message}\n\n")
                    append("Virtual Display ID: $displayId\n")
                    append("The display may be PRIVATE.\n")
                    append("Consider using Shizuku for full functionality.")
                }
            }
        }
    }

    /**
     * Direct touch handling: maps touch coordinates from the SurfaceView
     * to the virtual display coordinates and injects them via accessibility service.
     */
    private fun handleDirectTouch(view: View, event: MotionEvent): Boolean {
        val accService = NativeDexAccessibilityService.instance
        if (accService == null || virtualDisplayId == -1) return false

        // Map SurfaceView coordinates → virtual display coordinates
        val scaleX = VDISPLAY_WIDTH.toFloat() / view.width
        val scaleY = VDISPLAY_HEIGHT.toFloat() / view.height
        val vdX = event.x * scaleX
        val vdY = event.y * scaleY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Update cursor position on the virtual display
                accService.setCursorPosition(vdX, vdY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                accService.setCursorPosition(vdX, vdY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val downDuration = event.eventTime - event.downTime
                if (downDuration < 300) {
                    // Short tap → click
                    accService.injectClick(vdX, vdY, virtualDisplayId)
                } else {
                    // Long press
                    accService.injectLongClick(vdX, vdY, virtualDisplayId)
                }
                return true
            }
        }
        return false
    }

    private fun hasWriteSecureSettings(): Boolean {
        return checkCallingOrSelfPermission(
            "android.permission.WRITE_SECURE_SETTINGS"
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        virtualDisplayId = -1
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseVirtualDisplay()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }
}
