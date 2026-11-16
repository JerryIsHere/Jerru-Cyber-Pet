package com.jerru.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.lifecycle.LifecycleService
import kotlin.math.sin

class OverlayService : LifecycleService() {
    companion object {
        const val ACTION_START = "com.jerru.pet.START"
        const val ACTION_VISION_ON = "com.jerru.pet.VISION_ON"
        const val ACTION_VISION_OFF = "com.jerru.pet.VISION_OFF"
        const val ACTION_STOP = "com.jerru.pet.STOP"
        private const val CHANNEL = "jerru_overlay"
    }

    private val state = JerruStateMachine()
    private lateinit var windowManager: WindowManager
    private var eyeView: JerruEyeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var sensors: SensorController? = null
    private var vision: VisionController? = null
    private var lastPositionUpdate = 0L
    private var lastMusicUpdate = 0L

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Jerru companion", NotificationManager.IMPORTANCE_LOW))
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_VISION_OFF && eyeView == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val enableVision = intent?.action == ACTION_VISION_ON
        try {
            foreground(enableVision || vision != null)
        } catch (_: SecurityException) {
            foreground(false)
        }
        if (eyeView == null) {
            val density = resources.displayMetrics.density
            val layout = WindowManager.LayoutParams(
                (150 * density).toInt(), (58 * density).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            val view = JerruEyeView(this, state, ::positionPet)
            try {
                windowManager.addView(view, layout)
            } catch (_: WindowManager.BadTokenException) {
                stopSelf()
                return START_NOT_STICKY
            }
            params = layout
            eyeView = view
            state.screenOn = getSystemService(PowerManager::class.java).isInteractive
            sensors = SensorController(this, state).also { it.start() }
        }
        when (intent?.action) {
            ACTION_VISION_ON -> if (vision == null) {
                try {
                    foreground(true)
                    vision = VisionController(this, state).also { it.start() }
                } catch (_: SecurityException) {
                    foreground(false)
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
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                if (camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
            startForeground(1, notification, type)
        } else if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, notification, if (camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
        } else {
            startForeground(1, notification)
        }
    }

    private fun positionPet(state: EyeState, now: Long) {
        if (now - lastPositionUpdate < 32) return
        lastPositionUpdate = now
        if (now - lastMusicUpdate >= 1000) {
            state.musicPlaying = getSystemService(AudioManager::class.java).isMusicActive
            lastMusicUpdate = now
        }
        val layout = params ?: return
        val view = eyeView ?: return
        val metrics = resources.displayMetrics
        val maxX = ((metrics.widthPixels - layout.width) / 2).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - layout.height).coerceAtLeast(0)
        val x: Int
        val y: Int
        if (state.bedtime) {
            x = 0
            y = maxY / 2
        } else if (state.roaming) {
            val phase = (now % 30000L) / 30000f
            if (phase < 0.3f) {
                val progress = phase / 0.3f
                x = (sin(progress * Math.PI * 3).toFloat() * maxX * progress).toInt()
                y = (maxY * progress).toInt()
            } else {
                val distance = (phase - 0.3f) / 0.7f * (4 * maxX + 2 * maxY)
                when {
                    distance < 2 * maxX -> { x = maxX - distance.toInt(); y = maxY }
                    distance < 2 * maxX + maxY -> { x = -maxX; y = maxY - (distance - 2 * maxX).toInt() }
                    distance < 4 * maxX + maxY -> { x = -maxX + (distance - 2 * maxX - maxY).toInt(); y = 0 }
                    else -> { x = maxX; y = (distance - 4 * maxX - maxY).toInt() }
                }
            }
        } else {
            x = 0
            y = 0
        }
        if (layout.x != x || layout.y != y) {
            layout.x = x
            layout.y = y
            windowManager.updateViewLayout(view, layout)
        }
    }

    override fun onDestroy() {
        vision?.stop()
        sensors?.stop()
        eyeView?.let { windowManager.removeView(it) }
        eyeView = null
        super.onDestroy()
    }
}
