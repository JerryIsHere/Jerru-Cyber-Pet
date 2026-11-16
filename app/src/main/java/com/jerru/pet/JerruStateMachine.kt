package com.jerru.pet

import android.graphics.Color
import android.os.SystemClock
import kotlin.math.sin
import kotlin.random.Random

enum class Expression { NORMAL, HAPPY, ANGRY, TIRED, SURPRISED, DIZZY, SMUG, SLEEPING }

data class EyeState(
    val expression: Expression = Expression.NORMAL,
    val color: Int = Color.rgb(0, 229, 255),
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val blink: Float = 0f,
    val roaming: Boolean = false,
    val bedtime: Boolean = false
)

class JerruStateMachine {
    private val cyan = Color.rgb(0, 229, 255)
    private val red = Color.rgb(255, 23, 68)
    private val green = Color.rgb(0, 255, 102)
    private val amber = Color.rgb(255, 179, 0)
    private val purple = Color.rgb(213, 0, 249)
    private val slate = Color.rgb(84, 110, 122)
    private var lastInteraction = SystemClock.elapsedRealtime()
    private var nextLook = lastInteraction + 5000
    private var nextBlink = lastInteraction + 3000
    private var blinkStart = 0L
    private var idleX = 0f
    private var idleY = 0f
    private var dizzyUntil = 0L
    private var faceUntil = 0L
    private var faceExpression = Expression.NORMAL
    private var faceX = 0f
    private var yawnUntil = 0L
    var charging = false
    var lowBattery = false
    var hot = false
    var dark = false
    var tiltX = 0f
    var tiltY = 0f
    var screenOn = true
    var musicPlaying = false

    fun interact() { lastInteraction = SystemClock.elapsedRealtime() }
    fun shake() { dizzyUntil = SystemClock.elapsedRealtime() + 4000 }
    fun face(expression: Expression, x: Float) {
        faceExpression = expression
        faceX = x.coerceIn(-1f, 1f)
        faceUntil = SystemClock.elapsedRealtime() + 2500
    }
    fun clearFace() { faceUntil = 0L }

    fun update(now: Long = SystemClock.elapsedRealtime(), hour: Int): EyeState {
        val idle = now - lastInteraction
        if (now >= nextLook) {
            idleX = Random.nextFloat() * 2f - 1f
            idleY = if (Random.nextBoolean()) 0.65f else Random.nextFloat() - 0.5f
            if (idle >= 5000 && Random.nextInt(5) == 0) yawnUntil = now + 1200
            nextLook = now + Random.nextLong(5000, 30001)
        }
        if (now >= nextBlink) {
            blinkStart = now
            nextBlink = now + Random.nextLong(3000, 7000)
        }
        val blinkTime = now - blinkStart
        val blink = if (blinkStart > 0 && blinkTime in 0..240) {
            1f - kotlin.math.abs(blinkTime - 120f) / 120f
        } else 0f
        val bedtime = idle >= 60000 && screenOn && hour in 1..4
        val expression = when {
            hot || (now < faceUntil && faceExpression == Expression.ANGRY) || bedtime -> Expression.ANGRY
            now < dizzyUntil -> Expression.DIZZY
            dark -> Expression.SLEEPING
            now < faceUntil -> faceExpression
            lowBattery -> Expression.TIRED
            now < yawnUntil -> Expression.SURPRISED
            else -> Expression.NORMAL
        }
        val color = when {
            expression == Expression.ANGRY -> red
            dark || lowBattery -> slate
            charging -> green
            now - JerruNotificationListener.lastNotification in 0..5000 -> amber
            musicPlaying -> purple
            idle >= 5000 -> Color.HSVToColor(floatArrayOf((now / 35f) % 360f, 0.8f, 0.85f + 0.15f * sin(now / 600.0).toFloat()))
            else -> cyan
        }
        return EyeState(
            expression, color,
            if (now < faceUntil && faceExpression == Expression.ANGRY) faceX else (tiltX + if (idle >= 5000) idleX else 0f).coerceIn(-1f, 1f),
            (tiltY + if (idle >= 5000) idleY else 0f).coerceIn(-1f, 1f),
            blink, idle >= 60000 && screenOn && !dark, bedtime
        )
    }
}
