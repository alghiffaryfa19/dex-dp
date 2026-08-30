package com.fauzan.nativedex

import android.Manifest
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        
        val statusText = TextView(this).apply {
            text = "NativeDex Setup"
            textSize = 24f
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
        }
        layout.addView(statusText)

        val btnAssociate = Button(this).apply {
            text = "Request VDM Permission (Companion)"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val cdm = getSystemService(CompanionDeviceManager::class.java)
                    val request = AssociationRequest.Builder()
                        .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_APP_STREAMING)
                        .build()

                    cdm.associate(request, { intentSender ->
                        try {
                            startIntentSenderForResult(intentSender, REQUEST_CODE_ASSOCIATE, null, 0, 0, 0)
                        } catch (e: IntentSender.SendIntentException) {
                            e.printStackTrace()
                        }
                    }, null)
                } else {
                    statusText.text = "VDM requires Android 13+"
                }
            }
        }
        layout.addView(btnAssociate)

        val btnStart = Button(this).apply {
            text = "Start NativeDex Monitor"
            setOnClickListener {
                if (checkPermissions()) {
                    startMonitorService()
                    statusText.text = "Monitor Service is running.\nConnect HDMI to trigger Desktop mode."
                }
            }
        }
        layout.addView(btnStart)
        
        val btnAdbPair = Button(this).apply {
            text = "Pair Wireless Debugging (ADB)"
            setOnClickListener {
                if (!checkPermissions()) return@setOnClickListener
                scope.launch {
                    try {
                        val connected = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            val manager = Adb.createManager(this@MainActivity)
                            manager.autoConnect(this@MainActivity, 5_000)
                        }
                        if (connected) {
                            statusText.text = "ADB already connected!"
                        } else {
                            startService(Intent(this@MainActivity, PairingInputService::class.java))
                            statusText.text = "Follow pairing instructions in notification"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // libadb throws when pairing is required
                        startService(Intent(this@MainActivity, PairingInputService::class.java))
                        statusText.text = "Follow pairing instructions in notification"
                    }
                }
            }
        }
        layout.addView(btnAdbPair)
        
        val btnLaunchDeXBuiltIn = Button(this).apply {
            text = "Launch DeX on Built-in Display"
            setOnClickListener {
                if (!checkPermissions()) return@setOnClickListener
                scope.launch {
                    try {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            val manager = Adb.createManager(this@MainActivity)
                            if (manager.autoConnect(this@MainActivity, 5_000)) {
                                val cmd = "am start -n com.sec.android.app.launcher/com.honeyspace.dexservice.SecondaryLauncher --display 0"
                                Adb.runShell(manager, cmd)
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    statusText.text = "DeX command sent to display 0"
                                }
                            } else {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    statusText.text = "ADB not connected, please pair first."
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        statusText.text = "Failed: ${e.message}"
                    }
                }
            }
        }
        layout.addView(btnLaunchDeXBuiltIn)

        setContentView(layout)
    }
    
    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return false
            }
        }
        return true
    }

    private fun startMonitorService() {
        val intent = Intent(this, DisplayMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ASSOCIATE && resultCode == RESULT_OK) {
            val associationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, android.companion.AssociationInfo::class.java)
            } else null
            
            if (associationInfo != null) {
                val intent = Intent(this, VdmService::class.java).apply {
                    action = VdmService.ACTION_START_VIRTUAL_DEVICE
                    putExtra(VdmService.EXTRA_ASSOCIATION_ID, associationInfo.id)
                }
                startService(intent)
            }
        }
    }

    companion object {
        const val EXTRA_FROM_PAIRING = "com.fauzan.nativedex.FROM_PAIRING"
        private const val REQUEST_CODE_ASSOCIATE = 1001
    }
}
