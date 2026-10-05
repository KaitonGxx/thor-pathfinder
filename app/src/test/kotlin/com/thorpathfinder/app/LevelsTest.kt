package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {

    @Test
    fun upAndDownGoTopBothBottom() {
        assertEquals(LevelScreens.BOTH, LevelScreens.TOP.down)
        assertEquals(LevelScreens.BOTTOM, LevelScreens.BOTH.down)
        assertEquals(LevelScreens.BOTTOM, LevelScreens.BOTTOM.down)
        assertEquals(LevelScreens.BOTH, LevelScreens.BOTTOM.up)
        assertEquals(LevelScreens.TOP, LevelScreens.BOTH.up)
        assertEquals(LevelScreens.TOP, LevelScreens.TOP.up)
    }

    @Test
    fun aPressChangesOnlyTheScreensBeingEdited() {
        val levels = Levels.Pair(top = 6, bottom = 3)
        assertEquals(Levels.Pair(7, 3), Levels.nudge(levels, LevelScreens.TOP, 1, 0, 15))
        assertEquals(Levels.Pair(6, 4), Levels.nudge(levels, LevelScreens.BOTTOM, 1, 0, 15))
        assertEquals(Levels.Pair(7, 4), Levels.nudge(levels, LevelScreens.BOTH, 1, 0, 15))
    }

    @Test
    fun bothScreensMoveTogetherUntilEachReachesItsEnd() {
        // The gap stays while both can move; at an end, only the other keeps going.
        assertEquals(Levels.Pair(15, 12), Levels.nudge(Levels.Pair(13, 10), LevelScreens.BOTH, 2, 0, 15))
        assertEquals(Levels.Pair(15, 14), Levels.nudge(Levels.Pair(15, 12), LevelScreens.BOTH, 2, 0, 15))
        assertEquals(Levels.Pair(0, 1), Levels.nudge(Levels.Pair(2, 3), LevelScreens.BOTH, -2, 0, 15))
    }

    @Test
    fun brightnessNeverGoesBelowItsFloor() {
        val levels = Levels.Pair(3, 50)
        val down = Levels.nudge(levels, LevelScreens.TOP, -5, Levels.BRIGHTNESS_MIN, Levels.BRIGHTNESS_MAX)
        assertEquals(Levels.BRIGHTNESS_MIN, down.top)
    }

    @Test
    fun draggingBothKeepsTheGap() {
        val levels = Levels.Pair(top = 60, bottom = 40)
        assertEquals(Levels.Pair(80, 60), Levels.drag(levels, LevelScreens.BOTH, 80, 1, 100))
        assertEquals(Levels.Pair(60, 70), Levels.drag(levels, LevelScreens.BOTTOM, 70, 1, 100))
        assertEquals(Levels.Pair(10, 40), Levels.drag(levels, LevelScreens.TOP, 10, 1, 100))
    }

    @Test
    fun brightnessFollowsAndroidsSliderCurve() {
        assertEquals(0f, Levels.percentToBrightness(0), 0f)
        assertEquals(1f, Levels.percentToBrightness(100), 1e-4f)
        // Half way on Android's slider is about 1/12 of full brightness.
        assertEquals(1f / 12f, Levels.percentToBrightness(50), 1e-4f)
        // The test Thor's top screen, at Android's 0.3529, sits at 81% on Android's own slider.
        assertEquals(81, Levels.brightnessToPercent(0.3529412f))
    }

    @Test
    fun brightnessRoundTrips() {
        for (percent in 0..100) {
            assertEquals(percent, Levels.brightnessToPercent(Levels.percentToBrightness(percent)))
        }
    }

    @Test
    fun eachStepIsBrighterThanTheLast() {
        var last = -1f
        for (percent in 0..100 step 5) {
            val brightness = Levels.percentToBrightness(percent)
            assertTrue("$percent% is not brighter than the step before", brightness > last)
            last = brightness
        }
    }

    @Test
    fun storedBrightnessIsReadAsAndroidDoes() {
        assertEquals(0f, Levels.storedToBrightness(1), 0f)
        assertEquals(1f, Levels.storedToBrightness(255), 0f)
        assertEquals(0.3543f, Levels.storedToBrightness(91), 1e-3f)
    }
}
