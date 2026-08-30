package com.fauzan.nativedex

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VirtualDisplayTestActivity : AppCompatActivity() {

    private var virtualDisplay: VirtualDisplay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = FrameLayout(this)
        val surfaceView = SurfaceView(this)
        layout.addView(surfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        val statusText = TextView(this).apply {
            text = "Starting Virtual Display..."
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        layout.addView(statusText)
        setContentView(layout)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                statusText.text = "Surface Ready. Launching App..."
                createVirtualDisplay(holder, surfaceView.width, surfaceView.height)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                virtualDisplay?.release()
            }
        })
    }

    private fun createVirtualDisplay(holder: SurfaceHolder, width: Int, height: Int) {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                    
        virtualDisplay = displayManager.createVirtualDisplay(
            "NativeDexVD",
            width, height, metrics.densityDpi,
            holder.surface,
            flags
        )

        virtualDisplay?.let {
            val intent = Intent(this, VirtualDesktopActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val options = ActivityOptions.makeBasic()
            options.setLaunchDisplayId(it.display.displayId)
            startActivity(intent, options.toBundle())
        }
    }
}
