package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusModeTest {

    private val auto = FocusMode.AUTO
    private val top = FocusMode.TOP
    private val bottom = FocusMode.BOTTOM

    @Test
    fun cycleGoesRoundInTheMenusOrder() {
        // Auto-lock, Top screen, Bottom screen, then Auto-lock again.
        assertEquals(top, FocusMode.target(FocusSwitch.CYCLE, auto))
        assertEquals(bottom, FocusMode.target(FocusSwitch.CYCLE, top))
        assertEquals(auto, FocusMode.target(FocusSwitch.CYCLE, bottom))
    }

    @Test
    fun anUnknownValueCountsAsAutoLock() {
        assertEquals(top, FocusMode.next(-1))
        assertEquals(top, FocusMode.next(7))
    }

    @Test
    fun topAndBottomGoStraightThereFromAnywhere() {
        for (from in listOf(auto, top, bottom, -1)) {
            assertEquals(top, FocusMode.target(FocusSwitch.TOP, from))
            assertEquals(bottom, FocusMode.target(FocusSwitch.BOTTOM, from))
        }
    }

    @Test
    fun swapMovesBetweenTheScreensOnly() {
        assertEquals(bottom, FocusMode.target(FocusSwitch.SWAP, top))
        assertEquals(top, FocusMode.target(FocusSwitch.SWAP, bottom))
    }

    @Test
    fun swapFromAutoLockGoesToTheTopFirst() {
        assertEquals(top, FocusMode.target(FocusSwitch.SWAP, auto))
        assertEquals(top, FocusMode.target(FocusSwitch.SWAP, -1))
    }

    @Test
    fun namesAreAyns() {
        assertEquals("Auto-lock", FocusMode.name(auto))
        assertEquals("Top screen", FocusMode.name(top))
        assertEquals("Bottom screen", FocusMode.name(bottom))
        assertNull(FocusMode.name(3))
    }

    @Test
    fun theMessageNamesTheMode() {
        assertEquals("Focus Mode: Bottom screen", focusModeMessage(FocusMode.Outcome.Changed(bottom)))
        assertEquals("Focus Mode needs Shizuku", focusModeMessage(FocusMode.Outcome.NeedsShizuku))
    }
}
