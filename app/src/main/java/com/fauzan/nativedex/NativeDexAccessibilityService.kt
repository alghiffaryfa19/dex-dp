package com.fauzan.nativedex

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class NativeDexAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "NativeDexAccService"
        var instance: NativeDexAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility Service Connected")
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to process events, just inject them.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Accessibility Service Unbound")
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
            Log.e(TAG, "dispatchGesture returned false")
        }
    }
}
