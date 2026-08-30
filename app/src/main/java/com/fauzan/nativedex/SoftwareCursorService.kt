package com.fauzan.nativedex

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageView

class SoftwareCursorService : Service() {

    companion object {
        private const val TAG = "SoftwareCursorService"
        var instance: SoftwareCursorService? = null
            private set
    }

    private inner class LocalBinder : Binder() {
        fun getService(): SoftwareCursorService = this@SoftwareCursorService
    }
    private val binder = LocalBinder()

    private var cursorView: ImageView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var currentX = 0f
    private var currentY = 0f
    private var displayWidth = 1920
    private var displayHeight = 1080
    
    var activeDisplayId: Int = -1
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Cursor Service created")
        instance = this
        findAndAttachToExternalDisplay()
    }

    private fun findAndAttachToExternalDisplay() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays
        
        // Find an external display. If none, we will wait (or use a predefined one).
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
        val windowContext = displayContext.createWindowContext(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null
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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            Log.e(TAG, "Failed to add cursor view. Check SYSTEM_ALERT_WINDOW permission.", e)
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

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        if (cursorView != null) {
            windowManager?.removeView(cursorView)
        }
        instance = null
    }
}
