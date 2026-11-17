package com.jerru.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleService

class OverlayService : LifecycleService() {
    companion object {
        const val ACTION_START = "com.jerru.pet.START"
        const val ACTION_VISION_ON = "com.jerru.pet.VISION_ON"
        const val ACTION_VISION_OFF = "com.jerru.pet.VISION_OFF"
        const val ACTION_STOP = "com.jerru.pet.STOP"
        private const val CHANNEL = "jerru_overlay"
    }

    private val state = JerruStateMachine()
    private var sensors: SensorController? = null
    private var vision: VisionController? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Jerru companion", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP || (intent?.action == ACTION_VISION_OFF && sensors == null)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT < 30 || !JerruAccessibilityService.connected) {
            Toast.makeText(this, "Enable Jerru accessibility service on Android 11 or later", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        foreground(vision != null)
        if (sensors == null) {
            state.screenOn = getSystemService(PowerManager::class.java).isInteractive
            sensors = SensorController(this, state).also { it.start() }
            JerruAccessibilityService.startPet(state)
        }
        when (intent?.action) {
            ACTION_VISION_ON -> if (vision == null) {
                try {
                    foreground(true)
                    vision = VisionController(this, state) {
                        vision?.stop()
                        vision = null
                        state.clearFace()
                        foreground(false)
                        Toast.makeText(this, "Front camera unavailable", Toast.LENGTH_LONG).show()
                    }.also { it.start() }
                } catch (error: SecurityException) {
                    Log.e("Jerru", "Cannot start camera", error)
                    foreground(false)
                    Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
                }
            }
            ACTION_VISION_OFF -> {
                vision?.stop()
                vision = null
                state.clearFace()
                foreground(false)
            }
            ACTION_START -> state.interact()
        }
        return START_NOT_STICKY
    }

    private fun foreground(camera: Boolean) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Jerru is awake")
            .setContentText(if (camera) "Face reactions are enabled" else "Tap to manage your companion")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                if (camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
        } else if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, notification, if (camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        JerruAccessibilityService.stopPet()
        vision?.stop()
        sensors?.stop()
        super.onDestroy()
    }
}
