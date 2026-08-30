package com.fauzan.nativedex

import android.annotation.SuppressLint
import android.app.Service
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceParams
import android.companion.virtual.VirtualDevice
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi

class VdmService : Service() {

    private var virtualDevice: VirtualDevice? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var virtualMouse: android.hardware.input.VirtualMouse? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val action = intent?.action
            when (action) {
                ACTION_START_VIRTUAL_DEVICE -> {
                    val associationId = intent.getIntExtra(EXTRA_ASSOCIATION_ID, -1)
                    if (associationId != -1) {
                        startVirtualDevice(associationId)
                    }
                }
                ACTION_STOP_VIRTUAL_DEVICE -> {
                    stopVirtualDevice()
                }
            }
        }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun startVirtualDevice(associationId: Int) {
        if (virtualDevice != null) return

        val vdm = getSystemService(VirtualDeviceManager::class.java) ?: return
        
        try {
            val params = VirtualDeviceParams.Builder()
                .build()
                
            virtualDevice = vdm.createVirtualDevice(associationId, params)
            Log.i(TAG, "Virtual device created successfully.")
            
            // Expose a static instance so we can easily create displays and mice
            instance = this
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create virtual device", e)
        }
    }

    private fun stopVirtualDevice() {
        try {
            virtualMouse?.close()
            virtualMouse = null
            virtualDisplay?.release()
            virtualDisplay = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                virtualDevice?.close()
                virtualDevice = null
            }
            instance = null
            Log.i(TAG, "Virtual device stopped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing virtual device", e)
        }
        stopSelf()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createDisplay(surface: Surface, width: Int, height: Int, density: Int): VirtualDisplay? {
        if (virtualDevice == null) return null
        try {
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or 
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            virtualDisplay = virtualDevice?.createVirtualDisplay(
                width, height, density, surface, flags, null, null
            )
            return virtualDisplay
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create virtual display", e)
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun createMouse(): android.hardware.input.VirtualMouse? {
        if (virtualDevice == null || virtualDisplay == null) return null
        try {
            // In Android 14, VirtualMouse is created via VirtualDevice
            val mouseConfig = android.hardware.input.VirtualMouseConfig.Builder()
                .setAssociatedDisplayId(virtualDisplay!!.display.displayId)
                .setVendorId(0x1234)
                .setProductId(0x5678)
                .setInputDeviceName("Virtual Mouse")
                .build()
            
            virtualMouse = virtualDevice?.createVirtualMouse(mouseConfig)
            return virtualMouse
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create virtual mouse", e)
        }
        return null
    }
    
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun getVirtualMouse() = virtualMouse

    override fun onDestroy() {
        super.onDestroy()
        stopVirtualDevice()
    }

    companion object {
        private const val TAG = "VdmService"
        const val ACTION_START_VIRTUAL_DEVICE = "com.fauzan.nativedex.START_VDM"
        const val ACTION_STOP_VIRTUAL_DEVICE = "com.fauzan.nativedex.STOP_VDM"
        const val EXTRA_ASSOCIATION_ID = "associationId"
        
        @SuppressLint("StaticFieldLeak")
        var instance: VdmService? = null
            private set
    }
    
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getVirtualDevice(): VirtualDevice? {
        return virtualDevice
    }
}
