package com.fauzan.nativedex

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView

class NativeDexAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "NativeDexAccService"
        var instance: NativeDexAccessibilityService? = null
            private set
    }

    private var cursorView: ImageView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var currentX = 0f
    private var currentY = 0f
    private var displayWidth = 1920
    private var displayHeight = 1080
    
    var activeDisplayId: Int = -1
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility Service Connected")
        instance = this
        findAndAttachToExternalDisplay()
    }

    private fun findAndAttachToExternalDisplay() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays
        
        val targetDisplay = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        
        if (targetDisplay != null) {
            attachToDisplay(targetDisplay)
        } else {
            Log.w(TAG, "No external display found currently.")
        }
    }

    fun attachToDisplay(display: Display) {
        if (cursorView != null) {
            windowManager?.removeView(cursorView)
            cursorView = null
        }

        activeDisplayId = display.displayId
        displayWidth = display.mode.physicalWidth
        displayHeight = display.mode.physicalHeight
        
        Log.i(TAG, "Attaching cursor to display ${display.displayId} (${displayWidth}x${displayHeight})")

        val displayContext = createDisplayContext(display)
        
        // This is the magic! TYPE_ACCESSIBILITY_OVERLAY guarantees highest Z-Order
        val windowContext = displayContext.createWindowContext(
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null
        )

        windowManager = windowContext.getSystemService(WindowManager::class.java)

        cursorView = ImageView(windowContext).apply {
            setImageResource(R.drawable.ic_cursor)
        }

        currentX = displayWidth / 2f
        currentY = displayHeight / 2f

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentX.toInt()
            y = currentY.toInt()
        }

        try {
            windowManager?.addView(cursorView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add cursor view.", e)
        }
    }

    fun moveCursor(dx: Float, dy: Float) {
        if (cursorView == null || layoutParams == null) return

        currentX = (currentX + dx).coerceIn(0f, displayWidth.toFloat())
        currentY = (currentY + dy).coerceIn(0f, displayHeight.toFloat())

        layoutParams?.x = currentX.toInt()
        layoutParams?.y = currentY.toInt()

        windowManager?.updateViewLayout(cursorView, layoutParams)
    }

    fun getCursorPosition(): Pair<Float, Float> {
        return Pair(currentX, currentY)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Accessibility Service Unbound")
        if (cursorView != null) {
            windowManager?.removeView(cursorView)
            cursorView = null
        }
        instance = null
        return super.onUnbind(intent)
    }

    fun injectClick(x: Float, y: Float, displayId: Int) {
        if (displayId == -1) {
            Log.e(TAG, "Cannot inject click: displayId is invalid")
            return
        }

        val path = Path().apply {
            moveTo(x, y)
        }
        
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .setDisplayId(displayId)
            .build()
            
        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Click injected successfully at ($x, $y) on display $displayId")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "Click injection cancelled")
            }
        }, null)
        
        if (!result) {
            Log.e(TAG, "dispatchGesture returned false for click")
        }
    }

    fun injectLongClick(x: Float, y: Float, displayId: Int) {
        if (displayId == -1) return
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 600) // 600ms for long press
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .setDisplayId(displayId)
            .build()
        val result = dispatchGesture(gesture, null, null)
        if (!result) Log.e(TAG, "dispatchGesture returned false for long click")
    }

    fun injectSwipe(startX: Float, startY: Float, endX: Float, endY: Float, displayId: Int) {
        if (displayId == -1) return
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300) // 300ms swipe
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .setDisplayId(displayId)
            .build()
        val result = dispatchGesture(gesture, null, null)
        if (!result) Log.e(TAG, "dispatchGesture returned false for swipe")
    }
}
