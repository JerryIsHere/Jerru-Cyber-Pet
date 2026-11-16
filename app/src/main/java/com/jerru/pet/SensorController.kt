package com.jerru.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.SystemClock
import kotlin.math.sqrt

class SensorController(private val context: Context, private val state: JerruStateMachine) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private var lastShake = 0L
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    state.charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    state.lowBattery = level >= 0 && scale > 0 && level * 100 / scale < 20
                    state.hot = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) > 400
                }
                Intent.ACTION_SCREEN_ON -> { state.screenOn = true; state.interact() }
                Intent.ACTION_SCREEN_OFF -> state.screenOn = false
                Intent.ACTION_USER_PRESENT -> state.interact()
            }
        }
    }

    fun start() {
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        sensors.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
    }

    fun stop() {
        sensors.unregisterListener(this)
        context.unregisterReceiver(receiver)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> state.dark = event.values[0] < 5f
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                state.tiltX = (-x / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                state.tiltY = (y / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val now = SystemClock.elapsedRealtime()
                if (sqrt(x * x + y * y + z * z) > 24f && now - lastShake > 4000) {
                    lastShake = now
                    state.shake()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
