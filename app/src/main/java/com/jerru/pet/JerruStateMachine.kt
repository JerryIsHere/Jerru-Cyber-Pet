package com.jerru.pet

import android.os.SystemClock
import kotlin.math.abs
import kotlin.random.Random

enum class JerruExpression { NORMAL, HAPPY, SAD, ANGRY, SURPRISED, TIRED, SQUINT, CURIOUS, DIZZY }

data class EyeState(
    val expression: JerruExpression = JerruExpression.NORMAL,
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val blink: Float = 0f,
    val emerge: Float = 0f
)

class JerruStateMachine(initialTime: Long = SystemClock.elapsedRealtime()) {
    private var nextLook = initialTime + 2000
    private var nextBlink = initialTime + 3000
    private var blinkStart = 0L
    private var idleX = 0f
    private var idleY = 0f
    private var dizzyUntil = 0L
    private var faceUntil = 0L
    private var faceExpression = JerruExpression.NORMAL
    private var faceX = 0f
    private var brightSince: Long? = null
    private var emergeStart: Long? = null
    private var nextEmergeDelay = Random.nextLong(10_000, 15_001)
    var charging = false
    var lowBattery = false
    var hot = false
    var dark = false
    var tiltX = 0f
    var tiltY = 0f
    var screenOn = true

    fun interact() { nextLook = SystemClock.elapsedRealtime() + 2000 }
    fun shake() { dizzyUntil = SystemClock.elapsedRealtime() + 4000 }
    fun face(expression: JerruExpression, x: Float) {
        faceExpression = expression
        faceX = x.coerceIn(-1f, 1f)
        faceUntil = SystemClock.elapsedRealtime() + 2500
    }
    fun clearFace() { faceUntil = 0L }

    fun darkZoneAvailable(available: Boolean, now: Long) {
        if (available) {
            brightSince = null
            emergeStart = null
            nextEmergeDelay = Random.nextLong(10_000, 15_001)
        } else if (brightSince == null) {
            brightSince = now
        }
    }

    fun resetDarkZone() {
        brightSince = null
        emergeStart = null
    }

    fun update(now: Long = SystemClock.elapsedRealtime()): EyeState {
        if (now >= nextLook) {
            idleX = Random.nextFloat() * 2f - 1f
            idleY = Random.nextFloat() - 0.5f
            nextLook = now + Random.nextLong(1800, 5000)
        }
        if (now >= nextBlink) {
            blinkStart = now
            nextBlink = now + Random.nextLong(3000, 7000)
        }
        val blinkTime = now - blinkStart
        val blink = if (blinkStart > 0 && blinkTime in 0..240) {
            1f - abs(blinkTime - 120f) / 120f
        } else 0f
        val brightStart = brightSince
        if (screenOn && brightStart != null && emergeStart == null && now - brightStart >= nextEmergeDelay) {
            emergeStart = now
        }
        var elapsed = emergeStart?.let { now - it }
        if (elapsed != null && elapsed >= 4500) {
            emergeStart = null
            brightSince = now
            nextEmergeDelay = Random.nextLong(10_000, 15_001)
            elapsed = null
        }
        val emerge = if (!screenOn || elapsed == null) 0f else when {
            elapsed < 500 -> elapsed / 500f
            elapsed > 4000 -> (4500 - elapsed) / 500f
            else -> 1f
        }
        val expression = when {
            now < dizzyUntil -> JerruExpression.DIZZY
            hot -> JerruExpression.ANGRY
            dark || lowBattery -> JerruExpression.TIRED
            now < faceUntil -> faceExpression
            charging -> JerruExpression.HAPPY
            emerge > 0f -> if (elapsed != null && elapsed > 2200) JerruExpression.HAPPY else JerruExpression.CURIOUS
            JerruNotificationListener.lastNotification >= 0 &&
                now - JerruNotificationListener.lastNotification in 0..5000 -> JerruExpression.SURPRISED
            else -> JerruExpression.NORMAL
        }
        return EyeState(
            expression,
            ((if (now < faceUntil) faceX else idleX) + tiltX * 0.3f).coerceIn(-1f, 1f),
            (idleY + tiltY * 0.3f).coerceIn(-1f, 1f),
            blink,
            emerge
        )
    }
}
