package com.fauzan.nativedex

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fauzan.nativedex.shizuku.ShizukuSessionManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val statusText = TextView(this).apply {
            text = "NativeDex Setup"
            textSize = 24f
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, 32)
        }
        layout.addView(statusText)

        val btnVirtualDeX = Button(this).apply {
            text = "Launch DeX on Phone Screen (Shizuku)"
            setOnClickListener {
                if (ShizukuSessionManager.isShizukuAvailable()) {
                    if (ShizukuSessionManager.hasShizukuPermission()) {
                        startActivity(Intent(this@MainActivity, DexViewerActivity::class.java))
                    } else {
                        ShizukuSessionManager.requestPermission(1)
                        statusText.text = "Requesting Shizuku permission..."
                    }
                } else {
                    statusText.text = "Shizuku is not running. Please start Shizuku first."
                }
            }
        }
        layout.addView(btnVirtualDeX)

        setContentView(layout)
    }
}
