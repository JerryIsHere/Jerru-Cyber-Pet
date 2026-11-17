package com.jerru.pet

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors
import kotlin.math.abs

class JerruAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: JerruAccessibilityService? = null
        val connected: Boolean get() = instance != null
        fun startPet(state: JerruStateMachine) { instance?.start(state) }
        fun stopPet() { instance?.stop() }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val screenshotWorker = Executors.newSingleThreadExecutor()
    private lateinit var windows: WindowManager
    private var view: JerruEyeView? = null
    private var layout: WindowManager.LayoutParams? = null
    private var state: JerruStateMachine? = null
    private var target: DarkZone? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var lastScreenshot = 0L
    private var capturing = false
    private var generation = 0
    private var contentVersion = 0
    private var captureScheduled = false
    private val capture = Runnable {
        captureScheduled = false
        captureScreen()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windows = getSystemService(WindowManager::class.java)
        instance = this
    }

    private fun start(machine: JerruStateMachine) {
        if (view != null || Build.VERSION.SDK_INT < 30) return
        state = machine
        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (150 * density).toInt(), (80 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val eyeView = JerruEyeView(this, machine, ::onFrame)
        try {
            windows.addView(eyeView, params)
        } catch (error: RuntimeException) {
            Log.e("Jerru", "Cannot attach accessibility overlay", error)
            Toast.makeText(this, "Cannot display Jerru overlay", Toast.LENGTH_LONG).show()
            stopService(android.content.Intent(this, OverlayService::class.java))
            return
        }
        layout = params
        view = eyeView
        scheduleCapture(0)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (view == null || event?.packageName == packageName) return
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                contentVersion++
                if (target != null) {
                    target = null
                    view?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction {
                        if (target == null) view?.darkZoneVisible = false
                    }?.start()
                }
                scheduleCapture(500)
            }
        }
    }

    private fun scheduleCapture(delay: Long) {
        if (view == null || capturing) return
        if (captureScheduled && delay >= 3000) return
        if (captureScheduled) handler.removeCallbacks(capture)
        val rateLimit = (lastScreenshot + 1000 - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        captureScheduled = true
        handler.postDelayed(capture, if (delay < 3000) maxOf(0, rateLimit) else maxOf(delay, rateLimit))
    }

    private fun captureScreen() {
        if (Build.VERSION.SDK_INT < 30 || view == null || capturing) return
        if (state?.screenOn != true) {
            state?.resetDarkZone()
            target = null
            view?.darkZoneVisible = false
            scheduleCapture(3000)
            return
        }
        capturing = true
        val requestGeneration = generation
        view?.animate()?.cancel()
        view?.alpha = 0f
        // Allow the overlay's transparent frame to reach the compositor before capture.
        handler.postDelayed({
            if (requestGeneration == generation && view == null) {
                capturing = false
            } else if (requestGeneration == generation) {
                lastScreenshot = SystemClock.elapsedRealtime()
                val requestContentVersion = contentVersion
                val windowWidth = layout?.width ?: return@postDelayed
                val windowHeight = layout?.height ?: return@postDelayed
                try {
                    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        if (requestGeneration != generation || view == null) {
                            screenshot.hardwareBuffer.close()
                            return
                        }
                        screenshotWorker.execute {
                            try {
                                val result = analyzeScreenshot(screenshot, windowWidth, windowHeight)
                                handler.post {
                                    if (requestGeneration != generation) return@post
                                    if (requestContentVersion == contentVersion && state?.screenOn == true) {
                                        applyScreenshot(result)
                                    } else {
                                        captureFailed()
                                    }
                                    capturing = false
                                    scheduleCapture(if (requestContentVersion == contentVersion) 3000 else 0)
                                }
                            } catch (error: RuntimeException) {
                                Log.e("Jerru", "Cannot process screen capture", error)
                                handler.post {
                                    if (requestGeneration != generation) return@post
                                    captureFailed()
                                    capturing = false
                                    scheduleCapture(3000)
                                }
                            } finally {
                                screenshot.hardwareBuffer.close()
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (requestGeneration != generation) return
                        Log.w("Jerru", "Screen capture failed: $errorCode")
                        captureFailed()
                        capturing = false
                        scheduleCapture(3000)
                    }
                    })
                } catch (error: SecurityException) {
                    Log.e("Jerru", "Screen capture permission unavailable", error)
                    captureFailed()
                    capturing = false
                    scheduleCapture(3000)
                }
            }
        }, 120)
    }

    @RequiresApi(30)
    private fun analyzeScreenshot(screenshot: ScreenshotResult, windowWidth: Int, windowHeight: Int): ScreenAnalysis {
        val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
            ?: throw IllegalStateException("Screenshot buffer unavailable")
        try {
            val software = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("Screenshot copy unavailable")
            try {
                val scaled = Bitmap.createScaledBitmap(software, DarkZoneFinder.WIDTH, DarkZoneFinder.HEIGHT, true)
                try {
                    val pixels = IntArray(DarkZoneFinder.WIDTH * DarkZoneFinder.HEIGHT)
                    scaled.getPixels(pixels, 0, DarkZoneFinder.WIDTH, 0, 0, DarkZoneFinder.WIDTH, DarkZoneFinder.HEIGHT)
                    return ScreenAnalysis(software.width, software.height,
                        DarkZoneFinder.find(pixels, software.width, software.height, windowWidth, windowHeight))
                } finally {
                    if (scaled !== software) scaled.recycle()
                }
            } finally {
                software.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private data class ScreenAnalysis(val width: Int, val height: Int, val zone: DarkZone?)

    private fun applyScreenshot(result: ScreenAnalysis) {
        val params = layout ?: return
        screenWidth = result.width
        screenHeight = result.height
        target = result.zone
        state?.darkZoneAvailable(target != null, SystemClock.elapsedRealtime())
        view?.darkZoneVisible = false
        if (target == null) {
            params.x = ((screenWidth - params.width) / 2).coerceAtLeast(0)
            params.y = (screenHeight - params.height).coerceAtLeast(0)
            view?.let { windows.updateViewLayout(it, params) }
            view?.animate()?.alpha(1f)?.setDuration(200)?.start()
        }
    }

    private fun captureFailed() {
        target = null
        state?.resetDarkZone()
        view?.darkZoneVisible = false
        view?.alpha = 1f
    }

    private fun onFrame(eyeState: EyeState, now: Long) {
        val params = layout ?: return
        val eyeView = view ?: return
        val destination = target ?: return
        val dx = destination.x - params.x
        val dy = destination.y - params.y
        if (dx != 0 || dy != 0) {
            params.x += if (abs(dx) < 2) dx else (dx * .15f).toInt().let { if (it == 0) dx.sign() else it }
            params.y += if (abs(dy) < 2) dy else (dy * .15f).toInt().let { if (it == 0) dy.sign() else it }
            windows.updateViewLayout(eyeView, params)
        } else if (!capturing && !eyeView.darkZoneVisible) {
            eyeView.darkZoneVisible = true
            eyeView.animate().alpha(1f).setDuration(200).start()
        }
    }

    private fun Int.sign() = if (this < 0) -1 else 1

    private fun stop() {
        generation++
        handler.removeCallbacks(capture)
        captureScheduled = false
        capturing = false
        view?.let { windows.removeView(it) }
        view = null
        layout = null
        target = null
        state?.resetDarkZone()
        state = null
    }

    override fun onInterrupt() {
        stop()
        stopService(android.content.Intent(this, OverlayService::class.java))
    }

    override fun onDestroy() {
        stop()
        if (instance === this) instance = null
        stopService(android.content.Intent(this, OverlayService::class.java))
        screenshotWorker.shutdown()
        super.onDestroy()
    }
}
