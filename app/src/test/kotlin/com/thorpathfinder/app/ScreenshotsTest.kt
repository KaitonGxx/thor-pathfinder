package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScreenshotsTest {

    private val top = Screenshots.Screen.TOP
    private val bottom = Screenshots.Screen.BOTTOM

    @Test
    fun aShortcutFromBeforeTheChoiceKeepsAndroidsOwn() {
        // Stored Screenshot shortcuts carry no ".shot", and read as this default.
        assertEquals(ScreenshotTarget.ANDROID, Shortcut(ButtonAction.SCREENSHOT).shot)
        assertTrue(Screenshots.screens(ScreenshotTarget.ANDROID).isEmpty())
        assertTrue(Screenshots.fileNames(ScreenshotTarget.ANDROID, LocalDateTime.now()).isEmpty())
    }

    @Test
    fun itNeedsNoShizuku() {
        assertFalse(ButtonAction.SCREENSHOT.needsShizuku)
    }

    @Test
    fun theTopScreenIsAskedForFirst() {
        assertEquals(listOf(bottom), Screenshots.screens(ScreenshotTarget.BOTTOM))
        assertEquals(listOf(top, bottom), Screenshots.screens(ScreenshotTarget.BOTH))
        assertEquals(listOf(top, bottom), Screenshots.screens(ScreenshotTarget.BOTH_SEPARATE))
    }

    @Test
    fun theThorsScreensStackTopAboveBottomCentred() {
        // The top screen is 1920x1080 and the bottom 1240x1080, both landscape.
        val stack = Screenshots.stack(Screenshots.Size(1920, 1080), Screenshots.Size(1240, 1080))
        assertEquals(Screenshots.Size(1920, 2160), stack.size)
        assertEquals(Screenshots.Offset(0, 0), stack.top)
        assertEquals(Screenshots.Offset(340, 1080), stack.bottom)
    }

    @Test
    fun aWiderBottomScreenSetsTheWidth() {
        val stack = Screenshots.stack(Screenshots.Size(1000, 500), Screenshots.Size(1200, 600))
        assertEquals(Screenshots.Size(1200, 1100), stack.size)
        assertEquals(Screenshots.Offset(100, 0), stack.top)
        assertEquals(Screenshots.Offset(0, 500), stack.bottom)
    }

    @Test
    fun anOddGapLeavesTheSparePixelOnTheRight() {
        val stack = Screenshots.stack(Screenshots.Size(1921, 1080), Screenshots.Size(1240, 1080))
        assertEquals(340, stack.bottom.x)
    }

    @Test
    fun namesFollowAndroidsWithTheScreensAfterPathfinder() {
        val time = LocalDateTime.of(2026, 10, 22, 9, 5, 3)
        assertEquals(
            listOf("Screenshot_20261022-090503_Pathfinder_bottom.png"),
            Screenshots.fileNames(ScreenshotTarget.BOTTOM, time),
        )
        assertEquals(
            listOf("Screenshot_20261022-090503_Pathfinder_both.png"),
            Screenshots.fileNames(ScreenshotTarget.BOTH, time),
        )
    }

    @Test
    fun aPairSharesOneTime() {
        val time = LocalDateTime.of(2026, 1, 2, 23, 59, 59)
        assertEquals(
            listOf(
                "Screenshot_20260102-235959_Pathfinder_top.png",
                "Screenshot_20260102-235959_Pathfinder_bottom.png",
            ),
            Screenshots.fileNames(ScreenshotTarget.BOTH_SEPARATE, time),
        )
    }

    @Test
    fun theFirstRequestGoesAtOnce() {
        assertEquals(0L, Screenshots.waitBefore(null, 5000))
    }

    @Test
    fun theNextWaitsOutAndroidsGapAndALittleMore() {
        // Android refuses a request 333 ms or less after the last one.
        assertEquals(353L, Screenshots.waitBefore(1000, 1000))
        assertEquals(153L, Screenshots.waitBefore(1000, 1200))
        assertEquals(0L, Screenshots.waitBefore(1000, 1353))
        assertEquals(0L, Screenshots.waitBefore(1000, 9000))
    }

    @Test
    fun androidsErrorsBecomeReasons() {
        assertEquals(Screenshots.Problem.INTERNAL, Screenshots.problem(1))
        assertEquals(Screenshots.Problem.NO_ACCESS, Screenshots.problem(2))
        assertEquals(Screenshots.Problem.TOO_SOON, Screenshots.problem(3))
        assertEquals(Screenshots.Problem.SCREEN, Screenshots.problem(4))
        // 14's window errors, or anything newer, still say something.
        assertEquals(Screenshots.Problem.INTERNAL, Screenshots.problem(6))
    }

    @Test
    fun theMessageCountsTheImages() {
        assertEquals("Screenshot saved", screenshotMessage(English, Screenshots.Outcome.Saved(1)))
        assertEquals("Both screenshots saved", screenshotMessage(English, Screenshots.Outcome.Saved(2)))
    }

    @Test
    fun aFailureSaysWhy() {
        assertEquals("No second screen found", screenshotMessage(English, Screenshots.Outcome.NoSecondScreen))
        assertEquals("The other screen is off", screenshotMessage(English, Screenshots.Outcome.SecondScreenOff))
        assertEquals(
            "Screenshot not saved: too soon after the last one",
            screenshotMessage(English, Screenshots.Outcome.Failed(Screenshots.Problem.TOO_SOON)),
        )
        for (problem in Screenshots.Problem.entries) {
            assertTrue(screenshotMessage(English, Screenshots.Outcome.Failed(problem)).startsWith("Screenshot not saved: "))
        }
    }

    @Test
    fun theCardsAndThePictureNameTheScreens() {
        fun short(target: ScreenshotTarget) = English.text(R.string.map_screenshot_of, English.text(target.short))
        assertEquals("Screenshot (top)", short(ScreenshotTarget.ANDROID))
        assertEquals("Screenshot (both, 2 images)", short(ScreenshotTarget.BOTH_SEPARATE))
        assertEquals("Top screen (Android's own)", English.text(ScreenshotTarget.ANDROID.text))
    }
}
