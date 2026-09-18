package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** input_devices.txt is /proc/bus/input/devices from an AYN Thor. */
class KeyReplayTest {

    private val devices = javaClass.classLoader!!.getResource("input_devices.txt")!!.readText()

    @Test
    fun findsEachDeviceByName() {
        assertEquals("/dev/input/event1", KeyReplay.devicePath(devices, "gpio-keys"))
        assertEquals("/dev/input/event9", KeyReplay.devicePath(devices, "Odin Controller"))
        assertEquals("/dev/input/event10", KeyReplay.devicePath(devices, "ODIN Station Virtual Mouse"))
    }

    @Test
    fun needsTheWholeName() {
        // "fts_ts" is a prefix of "fts_ts_3", and the two are different screens.
        assertEquals("/dev/input/event6", KeyReplay.devicePath(devices, "fts_ts"))
        assertEquals("/dev/input/event5", KeyReplay.devicePath(devices, "fts_ts_3"))
        assertNull(KeyReplay.devicePath(devices, "gpio"))
        assertNull(KeyReplay.devicePath(devices, "No Such Device"))
    }

    @Test
    fun theAynButtonIsReplayedOnItsOwnDevice() {
        assertEquals("gpio-keys", PhysicalButton.AYN.device)
        assertEquals(194, PhysicalButton.AYN.scanCode)
        assertNull(PhysicalButton.AYN.normalAction)
    }

    @Test
    fun labelsSayWhatNormalDoes() {
        assertEquals("Normal (AYN menu)", normalLabel(PhysicalButton.AYN, Gesture.PRESS))
        assertEquals("Normal (long press)", normalLabel(PhysicalButton.AYN, Gesture.HOLD))
        assertEquals("Normal (Back)", normalLabel(PhysicalButton.BACK, Gesture.PRESS))
        assertEquals("Normal (Home)", normalLabel(PhysicalButton.HOME, Gesture.PRESS))
        assertEquals("Normal (a press)", normalLabel(PhysicalButton.HOME, Gesture.HOLD))
    }
}
