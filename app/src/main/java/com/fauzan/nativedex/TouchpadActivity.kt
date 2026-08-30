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
import android.os.Build
import androidx.appcompat.app.AppCompatActivity

class TouchpadActivity : AppCompatActivity() {

    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var isMoved = false

    private val clickThreshold = 10f // pixels
    private val clickTimeThreshold = 300L // ms
    private val sensitivity = 1.5f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
            text = "NativeDex Touchpad Active\nSwipe to move cursor, tap to click.\n(Physical mouse captured automatically)"
            setTextColor(android.graphics.Color.DKGRAY)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
        }
        layout.addView(statusText)
        setContentView(layout)

        layout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    downTime = System.currentTimeMillis()
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - lastX) * sensitivity
                    val dy = (event.y - lastY) * sensitivity
                    
                    if (Math.abs(dx) > clickThreshold || Math.abs(dy) > clickThreshold) {
                        isMoved = true
                    }
                    
                    NativeDexAccessibilityService.instance?.moveCursor(dx, dy)
                    
                    lastX = event.x
                    lastY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val upTime = System.currentTimeMillis()
                    if (!isMoved && (upTime - downTime) < clickTimeThreshold) {
                        // It's a click! Get current cursor position and inject
                        val accService = NativeDexAccessibilityService.instance
                        
                        if (accService != null) {
                            val (cX, cY) = accService.getCursorPosition()
                            val targetDisplayId = accService.activeDisplayId
                            accService.injectClick(cX, cY, targetDisplayId)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.requestPointerCapture()
        }
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x * sensitivity
                val dy = event.y * sensitivity
                NativeDexAccessibilityService.instance?.moveCursor(dx, dy)
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_DOWN -> {
                val accService = NativeDexAccessibilityService.instance
                if (accService != null) {
                    val (cX, cY) = accService.getCursorPosition()
                    val targetDisplayId = accService.activeDisplayId
                    accService.injectClick(cX, cY, targetDisplayId)
                }
                return true
            }
        }
        return super.onCapturedPointerEvent(event)
    }
}
