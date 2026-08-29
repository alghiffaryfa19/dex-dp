package com.fauzan.nativedex

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DesktopLauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Basic layout for desktop taskbar
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.DKGRAY)
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                100 // taskbar height
            )
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
        }

        val btnAppDrawer = Button(this).apply {
            text = "Apps"
            setOnClickListener {
                Toast.makeText(this@DesktopLauncherActivity, "App Drawer Clicked", Toast.LENGTH_SHORT).show()
                // TODO: Show list of installed apps to launch
                val intent = Intent(Intent.ACTION_MAIN, null)
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = packageManager.queryIntentActivities(intent, 0)
                if (apps.isNotEmpty()) {
                    val app = apps.first()
                    val launchIntent = packageManager.getLaunchIntentForPackage(app.activityInfo.packageName)
                    launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    startActivity(launchIntent)
                }
            }
        }
        layout.addView(btnAppDrawer)

        setContentView(layout)
    }
}
