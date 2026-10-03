package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenVolumeTest {

    @Test
    fun readsTheMediaLevel() {
        val output = "[V] will get volume\n[V] Connecting to AudioService\n[V] volume is 0 in range [0..15]\n"
        assertEquals(0, ScreenVolume.parseLevel(output))
        assertEquals(12, ScreenVolume.parseLevel("[V] volume is 12 in range [0..15]"))
        assertNull(ScreenVolume.parseLevel("Error: no AudioService"))
    }

    @Test
    fun readsTheBottomScreensSetting() {
        assertEquals(5, ScreenVolume.parseSetting("5\n"))
        assertEquals(0, ScreenVolume.parseSetting("0"))
        assertNull(ScreenVolume.parseSetting("null\n"))
        assertNull(ScreenVolume.parseSetting(""))
    }
}
