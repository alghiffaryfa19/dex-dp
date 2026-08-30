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
import android.hardware.input.VirtualMouse
import android.graphics.PointF
import android.os.Build
import androidx.appcompat.app.AppCompatActivity

class TouchpadActivity : AppCompatActivity() {

    private var displayId = 0
    private var virtualMouse: VirtualMouse? = null
    private var lastX = 0f
    private var lastY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system UI
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
            text = "Starting VDM Touchpad..."
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            virtualMouse = VdmService.instance?.getVirtualMouse() ?: VdmService.instance?.createMouse()
            if (virtualMouse != null) {
                statusText.text = "VDM Touchpad Active"
            } else {
                statusText.text = "Failed to obtain VirtualMouse"
            }
        } else {
            statusText.text = "VirtualMouse requires Android 14+"
        }

        layout.setOnTouchListener { _, event ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && virtualMouse != null) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.x
                        lastY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        if (dx != 0f || dy != 0f) {
                            virtualMouse?.sendRelativeEvent(android.hardware.input.VirtualMouseRelativeEvent.Builder()
                                .setRelativeX(dx)
                                .setRelativeY(dy)
                                .build())
                            lastX = event.x
                            lastY = event.y
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        // Check if it's a tap
                        if (event.eventTime - event.downTime < 200) {
                            val btnEvent = android.hardware.input.VirtualMouseButtonEvent.Builder()
                                .setButtonCode(android.hardware.input.VirtualMouseButtonEvent.BUTTON_PRIMARY)
                            virtualMouse?.sendButtonEvent(btnEvent.setAction(android.hardware.input.VirtualMouseButtonEvent.ACTION_BUTTON_PRESS).build())
                            virtualMouse?.sendButtonEvent(btnEvent.setAction(android.hardware.input.VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE).build())
                        }
                    }
                }
            }
            true
        }
    }
}
