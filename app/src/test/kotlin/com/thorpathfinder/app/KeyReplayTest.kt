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
    fun findsTheControllerInEveryStyle() {
        // AYN's Xbox style makes the controller anew under another name, on the same node.
        val xbox = devices.replace("N: Name=\"Odin Controller\"", "N: Name=\"Xbox Wireless Controller\"")
        assertEquals("/dev/input/event9", KeyReplay.devicePath(xbox, "Odin Controller"))
        val none = devices.replace("N: Name=\"Odin Controller\"", "N: Name=\"None Controller\"")
        assertEquals("/dev/input/event9", KeyReplay.devicePath(none, "Odin Controller"))
        assertEquals(listOf("gpio-keys"), KeyReplay.names("gpio-keys"))
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
    fun backAndHomeAreReplayedOnTheController() {
        // RetroArch binds to the real button, so a Normal Back must be the real key.
        assertEquals("Odin Controller", PhysicalButton.BACK.device)
        assertEquals("Odin Controller", PhysicalButton.HOME.device)
        // ...with Android's own action to fall back on.
        assertEquals(ButtonAction.BACK, PhysicalButton.BACK.normalAction)
        assertEquals(ButtonAction.HOME, PhysicalButton.HOME.normalAction)
    }

    @Test
    fun labelsSayWhatNormalDoes() {
        assertEquals("Normal (AYN menu)", normalLabel(English, PhysicalButton.AYN, Gesture.PRESS))
        assertEquals("Normal (long press)", normalLabel(English, PhysicalButton.AYN, Gesture.HOLD))
        assertEquals("Normal (Back)", normalLabel(English, PhysicalButton.BACK, Gesture.PRESS))
        assertEquals("Normal (Home)", normalLabel(English, PhysicalButton.HOME, Gesture.PRESS))
        assertEquals("Normal (a press)", normalLabel(English, PhysicalButton.HOME, Gesture.HOLD))
    }
}
