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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VirtualDisplayTestActivity : AppCompatActivity() {

    private var virtualDisplay: VirtualDisplay? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

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

        virtualDisplay?.let { vd ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val manager = Adb.createManager(this@VirtualDisplayTestActivity)
                        if (manager.autoConnect(this@VirtualDisplayTestActivity, 5_000)) {
                            val displayId = vd.display.displayId
                            val cmd = "am start -n com.sec.android.app.launcher/com.honeyspace.dexservice.SecondaryLauncher --display $displayId"
                            Adb.runShell(manager, cmd)
                            
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@VirtualDisplayTestActivity, "DeX command sent via ADB to display $displayId", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@VirtualDisplayTestActivity, "ADB not connected. Please pair first in Main Menu.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VirtualDisplayTestActivity, "ADB Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
