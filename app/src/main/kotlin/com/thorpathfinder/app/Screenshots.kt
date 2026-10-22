package com.thorpathfinder.app

import android.accessibilityservice.AccessibilityService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The rules of the screenshots Pathfinder takes itself (every
 * [ScreenshotTarget] but Android's own): which screens, in what order, how
 * two screens share one image, what the files are called, and how long to
 * wait between requests. [ScreenCapture] does the taking and saving.
 *
 * Android 13 takes one display per `AccessibilityService.takeScreenshot`
 * request and turns a service away with ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
 * when its last request was 333 ms ago or less, whichever display either was
 * for (AbstractAccessibilityServiceConnection). A request turned away as too
 * soon doesn't restart that clock; one that fails for any other reason does.
 */
object Screenshots {

    /** Android's minimum gap between one service's requests (hidden: ACCESSIBILITY_TAKE_SCREENSHOT_REQUEST_INTERVAL_TIMES_MS). */
    const val INTERVAL_MS = 333L

    /** A little over, since Android times the gap from when each request reaches it. */
    const val MARGIN_MS = 20L

    /** How many more times a request turned away as too soon is sent. */
    const val RETRIES = 3

    /** How long a message just taken down is given to leave the screen before the first picture. */
    const val CLEAR_MS = 150L

    /** Where Android keeps its own screenshots, so these sit beside them. */
    const val FOLDER = "Pictures/Screenshots"

    enum class Screen { TOP, BOTTOM }

    /** The screens [target] takes, in the order they are asked for: the top one first. */
    fun screens(target: ScreenshotTarget): List<Screen> = when (target) {
        ScreenshotTarget.ANDROID -> emptyList()
        ScreenshotTarget.BOTTOM -> listOf(Screen.BOTTOM)
        ScreenshotTarget.BOTH, ScreenshotTarget.BOTH_SEPARATE -> listOf(Screen.TOP, Screen.BOTTOM)
    }

    /**
     * The files [target] saves, named the way Android names its own
     * screenshots, Pathfinder standing where the app's name would and the
     * screens after it. Two of a pair share [time], the moment the shortcut ran.
     */
    fun fileNames(target: ScreenshotTarget, time: LocalDateTime): List<String> {
        val start = "Screenshot_" + STAMP.format(time) + "_Pathfinder_"
        return when (target) {
            ScreenshotTarget.ANDROID -> emptyList()
            ScreenshotTarget.BOTTOM -> listOf(start + "bottom.png")
            ScreenshotTarget.BOTH -> listOf(start + "both.png")
            ScreenshotTarget.BOTH_SEPARATE -> listOf(start + "top.png", start + "bottom.png")
        }
    }

    // Root, so the digits are 0 to 9 whatever the language.
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

    data class Size(val width: Int, val height: Int)

    data class Offset(val x: Int, val y: Int)

    /** Two screens in one image: its size, and where each screen's picture goes. */
    data class Stack(val size: Size, val top: Offset, val bottom: Offset)

    /**
     * The top screen above the bottom one, as the Thor holds them: as wide as
     * the wider, each centred across, black where a narrower one leaves room.
     */
    fun stack(top: Size, bottom: Size): Stack {
        val width = maxOf(top.width, bottom.width)
        return Stack(
            size = Size(width, top.height + bottom.height),
            top = Offset((width - top.width) / 2, 0),
            bottom = Offset((width - bottom.width) / 2, top.height),
        )
    }

    /** How long to wait before a request at [now], when the last went at [last] (null: none yet). */
    fun waitBefore(last: Long?, now: Long): Long =
        if (last == null) 0 else (last + INTERVAL_MS + MARGIN_MS - now).coerceAtLeast(0)

    /** Why nothing was saved. */
    enum class Problem { TOO_SOON, NO_ACCESS, SCREEN, INTERNAL, NOT_WRITTEN }

    sealed interface Outcome {
        data class Saved(val files: Int) : Outcome
        data object NoSecondScreen : Outcome
        data object SecondScreenOff : Outcome
        data class Failed(val problem: Problem) : Outcome
    }

    /**
     * Android 13's four reasons, as a [Problem]. Nothing says a secure window
     * was left out: the capture doesn't ask for secure layers, so such a
     * window comes out black, and Android 13 has no error for it
     * (ERROR_TAKE_SCREENSHOT_SECURE_WINDOW is 14's, and only for one window).
     */
    fun problem(error: Int): Problem = when (error) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> Problem.TOO_SOON
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> Problem.NO_ACCESS
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> Problem.SCREEN
        else -> Problem.INTERNAL
    }
}

/** What a Screenshot shortcut says once it is done; never before, or it would be in the picture. */
fun screenshotMessage(words: Words, outcome: Screenshots.Outcome): String = when (outcome) {
    is Screenshots.Outcome.Saved ->
        words.text(if (outcome.files > 1) R.string.msg_screenshots_saved else R.string.msg_screenshot_saved)
    Screenshots.Outcome.NoSecondScreen -> words.text(R.string.msg_no_second_screen)
    Screenshots.Outcome.SecondScreenOff -> words.text(R.string.msg_other_screen_off)
    is Screenshots.Outcome.Failed -> words.text(
        R.string.msg_screenshot_failed,
        words.text(
            when (outcome.problem) {
                Screenshots.Problem.TOO_SOON -> R.string.screenshot_too_soon
                Screenshots.Problem.NO_ACCESS -> R.string.screenshot_no_access
                Screenshots.Problem.SCREEN -> R.string.screenshot_bad_screen
                Screenshots.Problem.INTERNAL -> R.string.screenshot_internal
                Screenshots.Problem.NOT_WRITTEN -> R.string.screenshot_not_written
            },
        ),
    )
}
