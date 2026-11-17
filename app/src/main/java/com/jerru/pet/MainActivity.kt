package com.jerru.pet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        layout.addView(TextView(this).apply {
            text = "Jerru\nA little companion hiding in dark screen areas."
            textSize = 22f
            gravity = Gravity.CENTER
        })
        fun button(label: String, action: () -> Unit) {
            layout.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }
        button("Enable Jerru accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("Start Jerru") {
            if (Build.VERSION.SDK_INT < 30 || !JerruAccessibilityService.connected) {
                android.widget.Toast.makeText(this, "Android 11+ and Jerru accessibility service are required", android.widget.Toast.LENGTH_LONG).show()
                if (Build.VERSION.SDK_INT >= 30) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                startPet(OverlayService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
                }
            }
        }
        button("Enable face reactions") {
            if (Build.VERSION.SDK_INT < 30 || !JerruAccessibilityService.connected) {
                android.widget.Toast.makeText(this, "Enable Jerru accessibility service first", android.widget.Toast.LENGTH_LONG).show()
            } else if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
            } else {
                startPet(OverlayService.ACTION_VISION_ON)
            }
        }
        button("Disable face reactions") { startPet(OverlayService.ACTION_VISION_OFF) }
        button("Notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        button("Stop Jerru") { stopService(Intent(this, OverlayService::class.java)) }
        layout.addView(TextView(this).apply {
            text = "Jerru uses accessibility screenshots to find dark areas. Screenshots are analyzed on this device and are never saved or uploaded. Face reactions use the front camera only after you enable them. Android shows a camera indicator and a foreground notification. Camera images are not saved or uploaded."
            setPadding(0, 24, 0, 0)
        })
        setContentView(layout)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && JerruAccessibilityService.connected) {
            startPet(OverlayService.ACTION_VISION_ON)
        }
    }

    private fun startPet(action: String) {
        startForegroundService(Intent(this, OverlayService::class.java).setAction(action))
    }
}
