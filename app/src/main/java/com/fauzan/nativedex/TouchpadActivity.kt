package com.fauzan.nativedex

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.view.ViewGroup

class TouchpadActivity : AppCompatActivity() {

    private var lastX = 0f
    private var lastY = 0f
    private var lastHoverX = 0f
    private var lastHoverY = 0f
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
        
        // Root Layout
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
        }

        // Left Column (Touchpad + Buttons)
        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 8.5f)
        }

        // Touchpad Area
        val touchArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 8f)
            setBackgroundColor(Color.parseColor("#111111"))
        }
        val statusText = TextView(this).apply {
            text = "MAIN TOUCHPAD\nSwipe to move, tap to click\n(Physical mouse supported)"
            setTextColor(Color.parseColor("#444444"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }
        touchArea.addView(statusText)

        // Buttons Area
        val buttonsArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f)
        }
        val btnLeftClick = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundColor(Color.parseColor("#222222"))
            addView(TextView(this@TouchpadActivity).apply {
                text = "LEFT CLICK"
                setTextColor(Color.parseColor("#555555"))
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }
            })
        }
        val btnRightClick = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundColor(Color.parseColor("#333333"))
            addView(TextView(this@TouchpadActivity).apply {
                text = "RIGHT CLICK"
                setTextColor(Color.parseColor("#666666"))
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }
            })
        }
        buttonsArea.addView(btnLeftClick)
        buttonsArea.addView(btnRightClick)

        leftCol.addView(touchArea)
        leftCol.addView(buttonsArea)

        // Right Column (Scroll Area)
        val scrollArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f)
            setBackgroundColor(Color.parseColor("#444444"))
            addView(TextView(this@TouchpadActivity).apply {
                text = "S\nC\nR\nO\nL\nL"
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }
            })
        }

        mainLayout.addView(leftCol)
        mainLayout.addView(scrollArea)
        setContentView(mainLayout)

        // Touch Listeners
        touchArea.setOnTouchListener { _, event ->
            handleCursorMoveAndClick(event)
        }

        btnLeftClick.setOnClickListener {
            val accService = NativeDexAccessibilityService.instance
            if (accService != null) {
                val (cX, cY) = accService.getCursorPosition()
                accService.injectClick(cX, cY, accService.activeDisplayId)
            }
        }

        btnRightClick.setOnClickListener {
            val accService = NativeDexAccessibilityService.instance
            if (accService != null) {
                val (cX, cY) = accService.getCursorPosition()
                accService.injectLongClick(cX, cY, accService.activeDisplayId)
            }
        }

        var scrollLastY = 0f
        scrollArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    scrollLastY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - scrollLastY
                    if (Math.abs(dy) > 30f) {
                        val accService = NativeDexAccessibilityService.instance
                        if (accService != null) {
                            val (cX, cY) = accService.getCursorPosition()
                            // If user swipes UP (dy < 0), they want content to go up (scroll down).
                            // A physical swipe down on the screen moves content down (scrolls up).
                            val swipeDist = 150f * Math.signum(dy)
                            accService.injectSwipe(cX, cY, cX, cY + swipeDist, accService.activeDisplayId)
                        }
                        scrollLastY = event.y
                    }
                    true
                }
                else -> true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mainLayout.setOnCapturedPointerListener { _, event ->
                handleCapturedPointer(event)
            }
        }
    }
    
    private fun handleCursorMoveAndClick(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                downTime = System.currentTimeMillis()
                isMoved = false
                return true
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
                return true
            }
            MotionEvent.ACTION_UP -> {
                val upTime = System.currentTimeMillis()
                if (!isMoved && (upTime - downTime) < clickTimeThreshold) {
                    val accService = NativeDexAccessibilityService.instance
                    if (accService != null) {
                        val (cX, cY) = accService.getCursorPosition()
                        accService.injectClick(cX, cY, accService.activeDisplayId)
                    }
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
                if (vScroll != 0f) {
                    val accService = NativeDexAccessibilityService.instance
                    if (accService != null) {
                        val (cX, cY) = accService.getCursorPosition()
                        val swipeDist = 150f * Math.signum(vScroll)
                        accService.injectSwipe(cX, cY, cX, cY + swipeDist, accService.activeDisplayId)
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
                if (vScroll != 0f) {
                    val accService = NativeDexAccessibilityService.instance
                    if (accService != null) {
                        val (cX, cY) = accService.getCursorPosition()
                        val swipeDist = 150f * Math.signum(vScroll)
                        accService.injectSwipe(cX, cY, cX, cY + swipeDist, accService.activeDisplayId)
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
