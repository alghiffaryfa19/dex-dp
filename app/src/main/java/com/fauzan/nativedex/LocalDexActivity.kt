package com.fauzan.nativedex

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import android.app.ActivityOptions

class LocalDexActivity : AppCompatActivity() {

    private var dexService: INativeDexService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            dexService = INativeDexService.Stub.asInterface(service)
            isServiceBound = true
            Log.d("LocalDex", "Shizuku Service Connected")
            
            // Try to create virtual display if surface is already ready
            surfaceView?.holder?.surface?.let {
                if (it.isValid) {
                    setupVirtualDisplay(it)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dexService = null
            isServiceBound = false
            Log.d("LocalDex", "Shizuku Service Disconnected")
        }
    }

    private var surfaceView: SurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force landscape (recommended for DeX)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

        val layout = FrameLayout(this)
        surfaceView = SurfaceView(this).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    if (isServiceBound) {
                        setupVirtualDisplay(holder.surface)
                    }
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    try {
                        dexService?.destroyVirtualDisplay()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })
        }
        layout.addView(surfaceView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(layout)

        checkShizukuAndBind()
    }

    private fun checkShizukuAndBind() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku is not running!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0)
            Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        bindShizukuService()
                    } else {
                        Toast.makeText(this@LocalDexActivity, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            })
        } else {
            bindShizukuService()
        }
    }

    private fun bindShizukuService() {
        val userServiceArgs = Shizuku.UserServiceArgs(
            ComponentName(packageName, "com.fauzan.nativedex.shizuku.DexShizukuService")
        ).daemon(false).processNameSuffix("dex_service").debuggable(BuildConfig.DEBUG)

        Shizuku.bindUserService(userServiceArgs, serviceConnection)
    }

    private fun setupVirtualDisplay(surface: android.view.Surface) {
        try {
            val displayMetrics = resources.displayMetrics
            val width = 1920
            val height = 1080
            val density = displayMetrics.densityDpi // Or override with DeX specific density (e.g., 160 or 240)

            val displayId = dexService?.createTrustedVirtualDisplay(surface, width, height, density) ?: -1
            if (displayId != -1) {
                Log.d("LocalDex", "Virtual Display created with ID: $displayId")
                launchDeX(displayId)
            } else {
                Toast.makeText(this, "Failed to create virtual display", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error IPC: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchDeX(displayId: Int) {
        try {
            val dexIntent = Intent().apply {
                setClassName("com.sec.android.app.desktoplauncher", "com.sec.android.app.desktoplauncher.NewDesktopLauncher")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId
            startActivity(dexIntent, options.toBundle())
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to secondary launcher if standard desktop launcher is not found
            try {
                val fallbackIntent = Intent().apply {
                    setClassName("com.sec.android.app.launcher", "com.honeyspace.dexservice.SecondaryLauncher")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                val options = ActivityOptions.makeBasic()
                options.launchDisplayId = displayId
                startActivity(fallbackIntent, options.toBundle())
            } catch (ex: Exception) {
                Toast.makeText(this, "Failed to launch DeX: ${ex.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            try {
                dexService?.destroyVirtualDisplay()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            isServiceBound = false
        }
    }
    
    // Store args to unbind properly
    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, "com.fauzan.nativedex.shizuku.DexShizukuService")
        ).daemon(false).processNameSuffix("dex_service").debuggable(BuildConfig.DEBUG)
    }
}
