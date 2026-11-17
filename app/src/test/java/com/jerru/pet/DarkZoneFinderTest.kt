package com.jerru.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DarkZoneFinderTest {
    private val width = DarkZoneFinder.WIDTH
    private val height = DarkZoneFinder.HEIGHT

    @Test fun findsAreaLargeEnoughForWholeOverlay() {
        val pixels = IntArray(width * height) { 0xffffff }
        for (y in 20 until 30) for (x in 10 until 20) pixels[y * width + x] = 0
        assertEquals(DarkZone(10, 20), DarkZoneFinder.find(pixels, 36, 64, 10, 10))
        assertNull(DarkZoneFinder.find(pixels, 36, 64, 11, 10))
    }

    @Test fun rejectsBrightAndOversizedAreas() {
        assertNull(DarkZoneFinder.find(IntArray(width * height) { 0xffffff }, 36, 64, 10, 10))
        assertNull(DarkZoneFinder.find(IntArray(width * height), 36, 64, 37, 10))
    }
}
