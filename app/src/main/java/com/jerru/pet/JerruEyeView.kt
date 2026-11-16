package com.jerru.pet

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

class JerruEyeView(
    context: Context,
    private val stateMachine: JerruStateMachine,
    private val onFrame: (EyeState, Long) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val evaluator = ArgbEvaluator()
    private var color = Color.rgb(0, 229, 255)
    private var lookX = 0f
    private var lookY = 0f
    private var blink = 0f
    private fun lerp(start: Float, end: Float, factor: Float) = start + (end - start) * factor

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = android.os.SystemClock.elapsedRealtime()
        val state = stateMachine.update(now, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        color = evaluator.evaluate(0.08f, color, state.color) as Int
        lookX = lerp(lookX, state.lookX, 0.1f)
        lookY = lerp(lookY, state.lookY, 0.1f)
        blink = lerp(blink, state.blink, 0.45f)
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = Color.BLACK
        canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, paint)
        for (side in -1..1 step 2) {
            val scale = 1f + side * lookX * 0.16f
            val eyeW = w * 0.19f * scale
            val eyeH = h * 0.66f * scale * (1f - blink * 0.93f) *
                if (state.expression == Expression.SURPRISED) 1.15f else 1f
            val cx = w * (0.5f + side * 0.22f) + lookX * w * 0.035f
            val cy = h * 0.5f + lookY * h * 0.08f
            val rect = RectF(cx - eyeW / 2f, cy - eyeH / 2f, cx + eyeW / 2f, cy + eyeH / 2f)
            paint.color = color
            canvas.drawRoundRect(rect, eyeW * 0.28f, eyeW * 0.28f, paint)
            paint.color = Color.BLACK
            when (state.expression) {
                Expression.HAPPY -> canvas.drawOval(rect.left - eyeW * 0.2f, cy + eyeH * 0.08f, rect.right + eyeW * 0.2f, rect.bottom + eyeH * 0.5f, paint)
                Expression.ANGRY, Expression.SMUG -> {
                    val drop = if (state.expression == Expression.SMUG && side == 1) 0.1f else 0.4f
                    val path = Path().apply {
                        moveTo(rect.left, rect.top)
                        lineTo(rect.right, rect.top)
                        lineTo(rect.right, rect.top + eyeH * if (side == -1) drop else 0.05f)
                        lineTo(rect.left, rect.top + eyeH * if (side == 1) drop else 0.05f)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }
                Expression.TIRED, Expression.SLEEPING -> canvas.drawRect(rect.left, rect.top, rect.right, rect.top + eyeH * if (state.expression == Expression.SLEEPING) 0.92f else 0.55f, paint)
                Expression.DIZZY -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = eyeW * 0.08f
                    val path = Path()
                    for (i in 0..50) {
                        val angle = i * 0.4f + now / 200f
                        val radius = eyeW * 0.006f * i
                        val x = cx + cos(angle) * radius
                        val y = cy + sin(angle) * radius
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, paint)
                    paint.style = Paint.Style.FILL
                }
                else -> Unit
            }
        }
        onFrame(state, now)
        postInvalidateOnAnimation()
    }
}
