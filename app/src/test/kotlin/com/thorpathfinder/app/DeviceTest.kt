package com.thorpathfinder.app

import com.thorpathfinder.app.Device.Support
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTest {

    private val verified = "Thor_V1.0.0.377_20260206_165408_user"
    private val verifiedTime = Device.MIN_BUILD_TIME

    @Test
    fun theVerifiedFirmwareIsSupported() {
        assertEquals(Support.Supported("1.0.0.377"), Device.check("AYN", "AYN Thor", verified, verifiedTime))
    }

    @Test
    fun newerFirmwareIsSupported() {
        assertEquals(
            Support.Supported("1.0.0.400"),
            Device.check("AYN", "AYN Thor", "Thor_V1.0.0.400_20260501_101010_user", verifiedTime + 1),
        )
        assertEquals(
            Support.Supported("1.1.0.12"),
            Device.check("AYN", "AYN Thor", "Thor_V1.1.0.12_20260801_101010_user", verifiedTime + 1),
        )
    }

    @Test
    fun olderFirmwareIsNot() {
        assertEquals(
            Support.OldFirmware("1.0.0.300"),
            Device.check("AYN", "AYN Thor", "Thor_V1.0.0.300_20251120_101010_user", verifiedTime - 1),
        )
    }

    @Test
    fun theThorLiteIsWelcomedOnItsOwnFirmware() {
        // Its firmware is numbered on its own line, so the Thor's minimum doesn't apply.
        assertEquals(
            Support.Supported("1.0.0.52", "AYN Thor Lite"),
            Device.check("AYN", "AYN Thor Lite", "ThorLite_V1.0.0.52_20260601_101010_user", 0),
        )
        assertEquals(
            Support.Supported("ThorLite_2026_R1", "AYN Thor Lite"),
            Device.check("AYN", "AYN Thor Lite", "ThorLite_2026_R1", 0),
        )
    }

    @Test
    fun otherDevicesAreNot() {
        assertEquals(Support.NotAThor("AYN Odin2"), Device.check("AYN", "AYN Odin2", verified, verifiedTime))
        assertEquals(Support.NotAThor("Pixel 8"), Device.check("Google", "Pixel 8", verified, verifiedTime))
    }

    @Test
    fun anUnreadableFirmwareNameFallsBackToTheBuildDate() {
        assertEquals(Support.Supported("Thor_2026_R2"), Device.check("AYN", "AYN Thor", "Thor_2026_R2", verifiedTime + 1))
        assertEquals(Support.UnknownFirmware, Device.check("AYN", "AYN Thor", "Thor_2025_R9", verifiedTime - 1))
    }
}
