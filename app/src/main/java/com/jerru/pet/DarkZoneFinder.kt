package com.jerru.pet

import kotlin.math.ceil

data class DarkZone(val x: Int, val y: Int)

object DarkZoneFinder {
    const val WIDTH = 36
    const val HEIGHT = 64

    fun find(pixels: IntArray, screenWidth: Int, screenHeight: Int, windowWidth: Int, windowHeight: Int): DarkZone? {
        require(pixels.size == WIDTH * HEIGHT && screenWidth > 0 && screenHeight > 0)
        if (windowWidth > screenWidth || windowHeight > screenHeight) return null
        val cellsWide = ceil(windowWidth.toDouble() * WIDTH / screenWidth).toInt().coerceIn(1, WIDTH)
        val cellsHigh = ceil(windowHeight.toDouble() * HEIGHT / screenHeight).toInt().coerceIn(1, HEIGHT)
        val luminance = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (2126 * (c shr 16 and 255) + 7152 * (c shr 8 and 255) + 722 * (c and 255)) / 10000
        }
        var best = Double.MAX_VALUE
        var result: DarkZone? = null
        for (y in 0..HEIGHT - cellsHigh) for (x in 0..WIDTH - cellsWide) {
            var sum = 0
            var bright = 0
            for (dy in 0 until cellsHigh) for (dx in 0 until cellsWide) {
                val value = luminance[(y + dy) * WIDTH + x + dx]
                sum += value
                if (value > 80) bright++
            }
            val count = cellsWide * cellsHigh
            val average = sum.toDouble() / count
            if (average < 45 && bright * 20 <= count && average < best) {
                best = average
                result = DarkZone(
                    (x * screenWidth / WIDTH).coerceAtMost(screenWidth - windowWidth),
                    (y * screenHeight / HEIGHT).coerceAtMost(screenHeight - windowHeight)
                )
            }
        }
        return result
    }
}
