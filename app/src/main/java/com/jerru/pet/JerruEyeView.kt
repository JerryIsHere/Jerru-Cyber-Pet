package com.jerru.pet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

data class EyeParams(
    val centerX: Float = 0f, val centerY: Float = 0f,
    val scaleX: Float = 1f, val scaleY: Float = 1f, val angle: Float = 0f,
    val upperInnerRadius: Float = .4f, val upperOuterRadius: Float = .4f,
    val lowerInnerRadius: Float = .4f, val lowerOuterRadius: Float = .4f,
    val upperLidY: Float = 0f, val upperLidAngle: Float = 0f, val upperLidBend: Float = 0f,
    val lowerLidY: Float = 0f, val lowerLidAngle: Float = 0f, val lowerLidBend: Float = 0f,
    val faceAngle: Float = 0f, val faceScaleX: Float = 1f, val faceScaleY: Float = 1f,
    val parallax: Float = .12f
) {
    fun interpolate(to: EyeParams, t: Float): EyeParams {
        fun mix(a: Float, b: Float) = a + (b - a) * t
        return EyeParams(
            mix(centerX, to.centerX), mix(centerY, to.centerY),
            mix(scaleX, to.scaleX), mix(scaleY, to.scaleY), mix(angle, to.angle),
            mix(upperInnerRadius, to.upperInnerRadius), mix(upperOuterRadius, to.upperOuterRadius),
            mix(lowerInnerRadius, to.lowerInnerRadius), mix(lowerOuterRadius, to.lowerOuterRadius),
            mix(upperLidY, to.upperLidY), mix(upperLidAngle, to.upperLidAngle), mix(upperLidBend, to.upperLidBend),
            mix(lowerLidY, to.lowerLidY), mix(lowerLidAngle, to.lowerLidAngle), mix(lowerLidBend, to.lowerLidBend),
            mix(faceAngle, to.faceAngle), mix(faceScaleX, to.faceScaleX), mix(faceScaleY, to.faceScaleY),
            mix(parallax, to.parallax)
        )
    }
}

class JerruEyeView(
    context: Context,
    private val stateMachine: JerruStateMachine,
    private val onFrame: (EyeState, Long) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    private val lids = Path()
    private var outerGlow: Shader? = null
    private var innerHotspot: Shader? = null
    private val current = arrayOf(EyeParams(), EyeParams())
    private var lastFrame = 0L
    private var nextJitter = 0L
    private var jitterX = 0f
    private var jitterY = 0f
    private var lookX = 0f
    private var lookY = 0f
    private var blink = 0f
    var darkZoneVisible = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val eyeW = w * .22f
        val eyeH = h * .61f
        outerGlow = RadialGradient(0f, 0f, eyeW * .78f,
            intArrayOf(0x6600E5FF, 0x2200E5FF, Color.TRANSPARENT), floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
        innerHotspot = RadialGradient(0f, 0f, eyeH * .6f,
            intArrayOf(0x55FFFFFF, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    }

    private fun preset(expression: JerruExpression, side: Int, now: Long): EyeParams = when (expression) {
        JerruExpression.NORMAL -> EyeParams()
        JerruExpression.HAPPY -> EyeParams(lowerLidY = .24f, lowerLidBend = -.38f, upperOuterRadius = .7f)
        JerruExpression.SAD -> EyeParams(upperLidY = .25f, upperLidAngle = .22f * side, upperLidBend = .12f, lowerOuterRadius = .7f)
        JerruExpression.ANGRY -> EyeParams(upperLidY = .26f, upperLidAngle = -.32f * side, upperInnerRadius = .1f)
        JerruExpression.SURPRISED -> EyeParams(scaleX = 1.12f, scaleY = 1.2f, upperInnerRadius = .15f, upperOuterRadius = .15f, lowerInnerRadius = .15f, lowerOuterRadius = .15f)
        JerruExpression.TIRED -> EyeParams(upperLidY = .5f, upperLidBend = .05f)
        JerruExpression.SQUINT -> EyeParams(upperLidY = .3f, lowerLidY = .3f)
        JerruExpression.CURIOUS -> if (side < 0) EyeParams(scaleY = 1.15f, faceAngle = -7f) else EyeParams(scaleY = .8f, upperLidY = .25f, angle = -8f, faceAngle = -7f)
        JerruExpression.DIZZY -> EyeParams(
            angle = side * (22f + sin(now / 90f) * 6f),
            centerY = side * .06f,
            faceAngle = sin(now / 160f) * 5f
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.elapsedRealtime()
        val state = stateMachine.update(now)
        val dt = if (lastFrame == 0L) 1f / 60f else ((now - lastFrame) / 1000f).coerceIn(0f, .05f)
        lastFrame = now
        val smooth = 1f - exp(-10f * dt)
        val dart = 1f - exp(-20f * dt)
        lookX += (state.lookX - lookX) * dart
        lookY += (state.lookY - lookY) * dart
        blink += (state.blink - blink) * (1f - exp(-35f * dt))
        if (now >= nextJitter) {
            jitterX = Random.nextFloat() * 1.6f - .8f
            jitterY = Random.nextFloat() * 1.6f - .8f
            nextJitter = now + Random.nextLong(90, 240)
        }
        val w = width.toFloat()
        val h = height.toFloat()
        if (state.emerge > 0f && !darkZoneVisible) {
            canvas.save()
            canvas.translate(w / 2f, h / 2f)
            canvas.scale(state.emerge, state.emerge)
            paint.shader = RadialGradient(0f, 0f, w * .5f,
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT), floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP)
            canvas.scale(1f, h / w)
            canvas.drawCircle(0f, 0f, w * .5f, paint)
            paint.shader = null
            canvas.restore()
        }
        val visibility = if (darkZoneVisible) 1f else state.emerge
        if (visibility > 0f) {
            canvas.saveLayerAlpha(0f, 0f, w, h, (visibility * 255).toInt())
            if (!darkZoneVisible) canvas.scale(visibility, visibility, w / 2f, h / 2f)
            for (i in 0..1) {
                val side = if (i == 0) -1 else 1
                current[i] = current[i].interpolate(preset(state.expression, side, now), smooth)
                drawEye(canvas, current[i], side, w, h)
            }
            canvas.restore()
        }
        onFrame(state, now)
        postInvalidateOnAnimation()
    }

    private fun drawEye(canvas: Canvas, p: EyeParams, side: Int, w: Float, h: Float) {
        val eyeW = w * .22f
        val eyeH = h * .61f
        canvas.save()
        canvas.translate(w / 2f, h / 2f)
        canvas.rotate(p.faceAngle)
        canvas.scale(p.faceScaleX, p.faceScaleY)
        canvas.translate(side * w * .22f + lookX * w * .035f + p.centerX * w + jitterX,
            lookY * h * .07f + p.centerY * h + jitterY)
        canvas.rotate(p.angle)
        val perspective = 1f + side * lookX * p.parallax
        canvas.scale(p.scaleX * perspective, p.scaleY * perspective)
        val l = -eyeW / 2f
        val r = eyeW / 2f
        val t = -eyeH / 2f
        val b = eyeH / 2f
        val maxRadius = minOf(eyeW, eyeH) / 2f
        val ul = (if (side < 0) p.upperOuterRadius else p.upperInnerRadius).coerceIn(0f, 1f) * maxRadius
        val ur = (if (side < 0) p.upperInnerRadius else p.upperOuterRadius).coerceIn(0f, 1f) * maxRadius
        val ll = (if (side < 0) p.lowerOuterRadius else p.lowerInnerRadius).coerceIn(0f, 1f) * maxRadius
        val lr = (if (side < 0) p.lowerInnerRadius else p.lowerOuterRadius).coerceIn(0f, 1f) * maxRadius
        shape.reset()
        shape.moveTo(l + ul, t)
        shape.lineTo(r - ur, t); shape.quadTo(r, t, r, t + ur)
        shape.lineTo(r, b - lr); shape.quadTo(r, b, r - lr, b)
        shape.lineTo(l + ll, b); shape.quadTo(l, b, l, b - ll)
        shape.lineTo(l, t + ul); shape.quadTo(l, t, l + ul, t); shape.close()
        val top = t + eyeH * maxOf(p.upperLidY, blink * .5f)
        val bottom = b - eyeH * maxOf(p.lowerLidY, blink * .5f)
        lids.reset()
        lids.moveTo(l, top - p.upperLidAngle * eyeH / 2f)
        lids.quadTo(0f, top + p.upperLidBend * eyeH, r, top + p.upperLidAngle * eyeH / 2f)
        lids.lineTo(r, bottom + p.lowerLidAngle * eyeH / 2f)
        lids.quadTo(0f, bottom + p.lowerLidBend * eyeH, l, bottom - p.lowerLidAngle * eyeH / 2f)
        lids.close()
        canvas.save()
        canvas.scale(1f, eyeH / eyeW)
        paint.shader = outerGlow
        canvas.drawCircle(0f, 0f, eyeW * .78f, paint)
        paint.shader = null
        canvas.restore()
        canvas.clipPath(shape)
        canvas.clipPath(lids)
        paint.color = Color.rgb(0, 229, 255)
        canvas.drawPath(shape, paint)
        paint.shader = innerHotspot
        canvas.drawPath(shape, paint)
        paint.shader = null
        paint.color = 0x59000000
        paint.strokeWidth = 1f
        var y = t
        while (y <= b) {
            canvas.drawLine(l, y, r, y, paint)
            y += 3.5f
        }
        canvas.restore()
    }
}
