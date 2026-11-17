package com.jerru.pet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
            text = "Jerru\nA little companion for your camera cutout."
            textSize = 22f
            gravity = Gravity.CENTER
        })
        fun button(label: String, action: () -> Unit) {
            layout.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }
        button("Allow overlay") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button("Start Jerru") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startPet(OverlayService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
                }
            }
        }
        button("Enable face reactions") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
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
            text = "Face reactions use the front camera only after you enable them. Android shows a camera indicator and a foreground notification. No images are saved or uploaded. Notification reactions require separate access in Android settings."
            setPadding(0, 24, 0, 0)
        })
        setContentView(layout)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && Settings.canDrawOverlays(this)) {
            startPet(OverlayService.ACTION_VISION_ON)
        }
    }

    private fun startPet(action: String) {
        startForegroundService(Intent(this, OverlayService::class.java).setAction(action))
    }
}
