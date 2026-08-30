package com.fauzan.nativedex

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DisplayMonitorService : Service(), DisplayManager.DisplayListener {

    private lateinit var displayManager: DisplayManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var adbManager: AbsAdbConnectionManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NativeDex Monitor Running")
            .setContentText("Listening for HDMI connections...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(this, null)

        // Initialize ADB
        serviceScope.launch {
            adbManager = Adb.createManager(this@DisplayMonitorService)
            if (adbManager?.autoConnect(this@DisplayMonitorService, 10_000) == true) {
                Log.i(TAG, "ADB Connected successfully")
            } else {
                Log.e(TAG, "ADB Connection failed")
            }
            
            // Check displays already connected
            displayManager.displays.forEach { display ->
                handleNewDisplay(display.displayId)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.unregisterDisplayListener(this)
        adbManager?.close()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDisplayAdded(displayId: Int) {
        Log.i(TAG, "Display added: $displayId")
        handleNewDisplay(displayId)
    }

    override fun onDisplayRemoved(displayId: Int) {
        Log.i(TAG, "Display removed: $displayId")
    }

    override fun onDisplayChanged(displayId: Int) {
        // Handle changes if necessary
    }

    private fun handleNewDisplay(displayId: Int) {
        if (displayId == Display.DEFAULT_DISPLAY) return
        
        val display = displayManager.getDisplay(displayId) ?: return
        Log.i(TAG, "New external display detected: ${display.name} (flags: ${display.flags})")
        
        serviceScope.launch {
            try {
                val manager = adbManager
                if (manager != null) {
                    // Launch Samsung DeX on the HDMI display
                    val startDexCommand = "am start -n com.sec.android.app.launcher/com.honeyspace.dexservice.SecondaryLauncher -f 0x18000000 --display $displayId"
                    Log.i(TAG, "Running command: $startDexCommand")
                    Adb.runShell(manager, startDexCommand)

                    // Launch TouchpadActivity on the primary display
                    val touchpadIntent = Intent(this@DisplayMonitorService, TouchpadActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("displayId", displayId)
                    }
                    startActivity(touchpadIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring display $displayId", e)
            }
        }
    }

    private fun launchDesktopLauncher(displayId: Int) {
        val intent = Intent(this, DesktopLauncherActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        val options = android.app.ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        startActivity(intent, options.toBundle())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NativeDex Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "DisplayMonitorService"
        private const val CHANNEL_ID = "nativedex_service"
        private const val NOTIFICATION_ID = 1001
    }
}
