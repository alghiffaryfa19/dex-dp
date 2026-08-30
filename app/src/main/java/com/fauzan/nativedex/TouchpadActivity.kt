package com.fauzan.nativedex

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.view.ViewGroup
import android.hardware.display.DisplayManager
import android.content.Context
import android.view.Display

class TouchpadActivity : AppCompatActivity() {

    private var lastX = 0f
    private var lastY = 0f
    
    // For 2-finger scrolling
    private var scrollLastX = 0f
    private var scrollLastY = 0f
    
    // For physical mouse generic hover
    private var lastHoverX = 0f
    private var lastHoverY = 0f
    
    private var downTime = 0L
    private var isMoved = false
    private var maxPointersDown = 1
    private var lastUpTime = 0L
    private var isDraggingState = false

    private val clickThreshold = 10f // pixels
    private val clickTimeThreshold = 300L // ms
    private val sensitivity = 1.5f

    override fun onResume() {
        super.onResume()
        // Ensure accessibility service is tracking the external display
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val extDisplay = displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (extDisplay != null) {
            NativeDexAccessibilityService.instance?.attachToDisplay(extDisplay)
        }
    }

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
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#050505"))
        }

        val statusText = TextView(this).apply {
            text = "MULTI-TOUCHPAD\n• 1 Finger: Move & Tap to Left Click\n• 2 Fingers: Swipe to Scroll & Tap to Right Click\n• Double Tap & Hold: Drag\n(Physical Mouse Supported)"
            setTextColor(Color.parseColor("#444444"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }
        layout.addView(statusText)
        setContentView(layout)

        layout.setOnTouchListener { _, event ->
            handleMultiTouch(event)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layout.setOnCapturedPointerListener { _, event ->
                handleCapturedPointer(event)
            }
        }
    }
    
    private fun handleMultiTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // First finger down
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpTime < 250L) {
                    isDraggingState = true
                } else {
                    isDraggingState = false
                }
                
                lastX = event.x
                lastY = event.y
                downTime = currentTime
                isMoved = false
                maxPointersDown = 1
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger down
                maxPointersDown = Math.max(maxPointersDown, event.pointerCount)
                if (event.pointerCount == 2) {
                    scrollLastX = event.getX(0)
                    scrollLastY = event.getY(0)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    val dx = (event.x - lastX) * sensitivity
                    val dy = (event.y - lastY) * sensitivity
                    
                    if (Math.abs(dx) > clickThreshold || Math.abs(dy) > clickThreshold) {
                        if (!isMoved && isDraggingState) {
                            val accService = NativeDexAccessibilityService.instance
                            if (accService != null) {
                                val (cX, cY) = accService.getCursorPosition()
                                accService.startDrag(cX, cY, accService.activeDisplayId)
                            }
                        }
                        isMoved = true
                    }
                    
                    NativeDexAccessibilityService.instance?.moveCursor(dx, dy)
                    
                    if (isMoved && isDraggingState) {
                        val accService = NativeDexAccessibilityService.instance
                        if (accService != null) {
                            val (cX, cY) = accService.getCursorPosition()
                            accService.updateDrag(cX, cY, accService.activeDisplayId)
                        }
                    }
                    
                    lastX = event.x
                    lastY = event.y
                } else if (event.pointerCount == 2) {
                    val dx = event.getX(0) - scrollLastX
                    val dy = event.getY(0) - scrollLastY
                    
                    if (Math.abs(dx) > 20f || Math.abs(dy) > 20f) {
                        isMoved = true
                        val accService = NativeDexAccessibilityService.instance
                        if (accService != null) {
                            val (cX, cY) = accService.getCursorPosition()
                            
                            val swipeDistX = 150f * Math.signum(dx)
                            val swipeDistY = 150f * Math.signum(dy)
                            
                            // Send a swipe on the screen
                            accService.injectSwipe(cX, cY, cX + swipeDistX, cY + swipeDistY, accService.activeDisplayId)
                        }
                        scrollLastX = event.getX(0)
                        scrollLastY = event.getY(0)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                // Wait for all fingers to be lifted before triggering clicks
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val upTime = System.currentTimeMillis()
                    
                    if (isDraggingState && isMoved) {
                        val accService = NativeDexAccessibilityService.instance
                        if (accService != null) {
                            val (cX, cY) = accService.getCursorPosition()
                            accService.endDrag(cX, cY, accService.activeDisplayId)
                        }
                    } else if (!isMoved && (upTime - downTime) < clickTimeThreshold) {
                        val accService = NativeDexAccessibilityService.instance
                        if (accService != null) {
                            val (cX, cY) = accService.getCursorPosition()
                            if (maxPointersDown == 1) {
                                accService.injectClick(cX, cY, accService.activeDisplayId)
                            } else if (maxPointersDown >= 2) {
                                accService.injectLongClick(cX, cY, accService.activeDisplayId)
                            }
                        }
                    }
                    
                    lastUpTime = upTime
                    isDraggingState = false
                }
                return true
            }
        }
        return false
    }

    private fun handleCapturedPointer(event: MotionEvent): Boolean {
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
                    
                    if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) {
                        accService.injectLongClick(cX, cY, accService.activeDisplayId)
                    } else {
                        accService.injectClick(cX, cY, accService.activeDisplayId)
                    }
                }
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                
                val accService = NativeDexAccessibilityService.instance
                if (accService != null) {
                    val (cX, cY) = accService.getCursorPosition()
                    
                    if (vScroll != 0f) {
                        val swipeDistY = 150f * Math.signum(vScroll)
                        accService.injectSwipe(cX, cY, cX, cY + swipeDistY, accService.activeDisplayId)
                    } else if (hScroll != 0f) {
                        val swipeDistX = 150f * Math.signum(hScroll)
                        accService.injectSwipe(cX, cY, cX + swipeDistX, cY, accService.activeDisplayId)
                    }
                }
                return true
            }
        }
        return false
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                lastHoverX = event.x
                lastHoverY = event.y
                return true
            }
            MotionEvent.ACTION_HOVER_MOVE -> {
                val dx = (event.x - lastHoverX) * sensitivity
                val dy = (event.y - lastHoverY) * sensitivity
                NativeDexAccessibilityService.instance?.moveCursor(dx, dy)
                lastHoverX = event.x
                lastHoverY = event.y
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                
                val accService = NativeDexAccessibilityService.instance
                if (accService != null) {
                    val (cX, cY) = accService.getCursorPosition()
                    
                    if (vScroll != 0f) {
                        val swipeDistY = 150f * Math.signum(vScroll)
                        accService.injectSwipe(cX, cY, cX, cY + swipeDistY, accService.activeDisplayId)
                    } else if (hScroll != 0f) {
                        val swipeDistX = 150f * Math.signum(hScroll)
                        accService.injectSwipe(cX, cY, cX + swipeDistX, cY, accService.activeDisplayId)
                    }
                }
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                val accService = NativeDexAccessibilityService.instance
                if (accService != null) {
                    val (cX, cY) = accService.getCursorPosition()
                    if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) {
                        accService.injectLongClick(cX, cY, accService.activeDisplayId)
                    } else {
                        accService.injectClick(cX, cY, accService.activeDisplayId)
                    }
                }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.requestPointerCapture()
        }
    }
}
