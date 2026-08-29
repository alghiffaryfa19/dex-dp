package com.fauzan.nativedex

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fauzan.nativedex.scrcpy.ScrcpySession
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TouchpadActivity : AppCompatActivity() {

    private var session: ScrcpySession? = null
    private var displayId = 0
    private var extWidth = 1920
    private var extHeight = 1080

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system UI (make it a black fullscreen touchpad)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        val layout = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        val statusText = TextView(this).apply {
            text = "Starting Touchpad..."
            setTextColor(android.graphics.Color.GRAY)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
        }
        layout.addView(statusText)
        setContentView(layout)

        displayId = intent.getIntExtra("displayId", -1)
        if (displayId == -1) {
            statusText.text = "Error: No display ID provided."
            return
        }

        // Get external display dimensions
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(displayId)
        if (display != null) {
            val metrics = android.util.DisplayMetrics()
            display.getRealMetrics(metrics)
            extWidth = metrics.widthPixels
            extHeight = metrics.heightPixels
        }

        layout.setOnTouchListener { view, event ->
            // Forward events to scrcpy controller
            session?.controller?.handleTouch(event, view.width, view.height, extWidth, extHeight)
            true
        }

        startScrcpySession(statusText)
    }

    private fun startScrcpySession(statusText: TextView) {
        val scrcpy = ScrcpySession(this, displayId)
        session = scrcpy

        lifecycleScope.launch {
            scrcpy.state.collectLatest { state ->
                when (state) {
                    is ScrcpySession.State.Starting -> {
                        statusText.text = state.message
                    }
                    is ScrcpySession.State.Running -> {
                        statusText.text = "Touchpad Active (Screen Off)"
                    }
                    is ScrcpySession.State.Stopped -> {
                        val msg = state.error ?: "Stopped"
                        statusText.text = msg
                    }
                }
            }
        }

        lifecycleScope.launch {
            scrcpy.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.stop()
    }
}
