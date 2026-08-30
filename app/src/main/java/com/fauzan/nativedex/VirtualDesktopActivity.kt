package com.fauzan.nativedex

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class VirtualDesktopActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.DKGRAY)
            setPadding(64, 64, 64, 64)
            gravity = android.view.Gravity.CENTER
        }

        val text = TextView(this).apply {
            text = "Welcome to Virtual Desktop!"
            textSize = 32f
            setTextColor(android.graphics.Color.WHITE)
        }
        layout.addView(text)

        val button = Button(this).apply {
            text = "Click Me!"
            setOnClickListener {
                Toast.makeText(this@VirtualDesktopActivity, "Button Clicked in Virtual Display!", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(button)

        setContentView(layout)
    }
}
