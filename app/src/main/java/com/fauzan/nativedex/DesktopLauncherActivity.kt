package com.fauzan.nativedex

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DesktopLauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen immersive mode
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )

        val layout = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val surfaceView = SurfaceView(this)
        layout.addView(surfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val statusText = TextView(this).apply {
            text = "Waiting for VDM Surface..."
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
        }
        layout.addView(statusText)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                statusText.text = "Surface Ready. Starting VDM Display..."
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val display = VdmService.instance?.createDisplay(
                        holder.surface,
                        surfaceView.width,
                        surfaceView.height,
                        resources.displayMetrics.densityDpi
                    )
                    if (display != null) {
                        statusText.visibility = View.GONE
                        Log.i("DesktopLauncher", "VDM Display started on Surface")
                    } else {
                        statusText.text = "VDM Not Initialized. Please pair first."
                    }
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })

        setContentView(layout)
    }
}
