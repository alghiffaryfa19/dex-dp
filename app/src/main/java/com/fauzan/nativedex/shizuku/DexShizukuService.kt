package com.fauzan.nativedex.shizuku

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Surface
import com.fauzan.nativedex.INativeDexService
import kotlin.system.exitProcess
import android.annotation.SuppressLint

class DexShizukuService : INativeDexService.Stub() {

    private var virtualDisplay: VirtualDisplay? = null

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun getSystemContext(): Context {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
        var currentActivityThread = currentActivityThreadMethod.invoke(null)
        if (currentActivityThread == null) {
            val systemMainMethod = activityThreadClass.getDeclaredMethod("systemMain")
            currentActivityThread = systemMainMethod.invoke(null)
        }
        val getSystemContextMethod = activityThreadClass.getDeclaredMethod("getSystemContext")
        return getSystemContextMethod.invoke(currentActivityThread) as Context
    }

    override fun createTrustedVirtualDisplay(surface: Surface, width: Int, height: Int, densityDpi: Int): Int {
        val context = getSystemContext()
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        
        // VIRTUAL_DISPLAY_FLAG_TRUSTED is 1 << 10 (1024)
        val VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 shl 10
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or 
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or 
                    VIRTUAL_DISPLAY_FLAG_TRUSTED

        return try {
            virtualDisplay?.release()
            virtualDisplay = displayManager.createVirtualDisplay(
                "NativeDexTrusted",
                width,
                height,
                densityDpi,
                surface,
                flags
            )
            virtualDisplay?.display?.displayId ?: -1
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    override fun destroyVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        // Exit process when done
        exitProcess(0)
    }
}
