package com.thorpathfinder.app

import com.thorpathfinder.app.ButtonAction.MOUSE_MODE
import com.thorpathfinder.app.ButtonAction.NORMAL
import com.thorpathfinder.app.ButtonAction.RECENTS
import com.thorpathfinder.app.ButtonAction.SCREENSHOT
import com.thorpathfinder.app.ButtonAction.SWAP_SCREENS
import com.thorpathfinder.app.Gesture.DOUBLE
import com.thorpathfinder.app.Gesture.HOLD
import com.thorpathfinder.app.Gesture.PRESS
import com.thorpathfinder.app.PhysicalButton.BACK
import com.thorpathfinder.app.PhysicalButton.HOME
import com.thorpathfinder.app.PhysicalButton.SELECT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A clock that only moves when told to, running due tasks in time order. */
private class FakeClock : Scheduler {
    var now = 0L
        private set
    private class Task(val at: Long, val run: () -> Unit)
    private val tasks = mutableListOf<Task>()

    override fun schedule(delayMs: Long, task: () -> Unit): Cancellable {
        val t = Task(now + delayMs, task)
        tasks += t
        return Cancellable { tasks.remove(t) }
    }

    fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val next = tasks.filter { it.at <= end }.minByOrNull { it.at } ?: break
            tasks.remove(next)
            now = next.at
            next.run()
        }
        now = end
    }
}

private class Config(private val map: Map<Pair<PhysicalButton, Gesture>, ButtonAction>) : GestureConfig {
    override fun action(button: PhysicalButton, gesture: Gesture) = map[button to gesture] ?: NORMAL
    override val holdMs = 800L
    override val doubleMs = 300L
}

class GestureEngineTest {

    private val clock = FakeClock()
    private val fired = mutableListOf<Triple<PhysicalButton, Gesture, ButtonAction>>()

    private fun engine(vararg map: Pair<Pair<PhysicalButton, Gesture>, ButtonAction>) =
        GestureEngine(Config(map.toMap()), clock) { b, g, a -> fired += Triple(b, g, a) }

    private val wayfinderLike = arrayOf(
        (BACK to DOUBLE) to RECENTS,
        (BACK to HOLD) to SWAP_SCREENS,
        (SELECT to DOUBLE) to MOUSE_MODE,
    )

    /** Presses and releases [button] after [heldMs]; returns whether down and up were consumed. */
    private fun GestureEngine.tap(button: PhysicalButton, heldMs: Long = 80): Pair<Boolean, Boolean> {
        val down = onKey(button, down = true, repeat = 0, time = clock.now)
        clock.advance(heldMs)
        val up = onKey(button, down = false, repeat = 0, time = clock.now)
        return down to up
    }

    @Test
    fun untouchedSystemButtonPassesThroughWithoutDelay() {
        val e = engine(*wayfinderLike)
        assertEquals(false to false, e.tap(HOME))
        clock.advance(1000)
        assertTrue(fired.isEmpty())
    }

    @Test
    fun singleBackWaitsForTheDoubleGapThenActsNormally() {
        val e = engine(*wayfinderLike)
        assertEquals(true to true, e.tap(BACK))
        assertTrue(fired.isEmpty())
        clock.advance(299)
        assertTrue(fired.isEmpty())
        clock.advance(1)
        assertEquals(listOf(Triple(BACK, PRESS, NORMAL)), fired)
    }

    @Test
    fun doubleBackOpensRecentsAndNothingElse() {
        val e = engine(*wayfinderLike)
        e.tap(BACK)
        clock.advance(120)
        e.tap(BACK)
        clock.advance(1000)
        assertEquals(listOf(Triple(BACK, DOUBLE, RECENTS)), fired)
    }

    @Test
    fun holdingBackSwapsWhileStillHeld() {
        val e = engine(*wayfinderLike)
        assertTrue(e.onKey(BACK, down = true, repeat = 0, time = 0))
        clock.advance(799)
        assertTrue(fired.isEmpty())
        clock.advance(1)
        assertEquals(listOf(Triple(BACK, HOLD, SWAP_SCREENS)), fired)
        // auto-repeat and the release are swallowed, and add nothing
        assertTrue(e.onKey(BACK, down = true, repeat = 1, time = clock.now))
        clock.advance(500)
        assertTrue(e.onKey(BACK, down = false, repeat = 0, time = clock.now))
        clock.advance(1000)
        assertEquals(1, fired.size)
    }

    @Test
    fun releasingBeforeTheHoldTimeIsAPress() {
        val e = engine(*wayfinderLike)
        e.tap(BACK, heldMs = 700)
        clock.advance(1000)
        assertEquals(listOf(Triple(BACK, PRESS, NORMAL)), fired)
    }

    @Test
    fun withoutADoublePressShortcutAPressActsAtOnce() {
        val e = engine((HOME to PRESS) to SCREENSHOT)
        e.tap(HOME)
        assertEquals(listOf(Triple(HOME, PRESS, SCREENSHOT)), fired)
    }

    @Test
    fun holdingTheSecondPressOfAPairIsAHoldOnly() {
        val e = engine(*wayfinderLike)
        e.tap(BACK)
        clock.advance(100)
        e.tap(BACK, heldMs = 900)
        clock.advance(1000)
        assertEquals(listOf(Triple(BACK, HOLD, SWAP_SCREENS)), fired)
    }

    @Test
    fun aCancelledPressDoesNothing() {
        val e = engine(*wayfinderLike)
        assertTrue(e.onKey(BACK, down = true, repeat = 0, time = 0))
        clock.advance(100)
        assertTrue(e.onKey(BACK, down = false, repeat = 0, time = clock.now, canceled = true))
        clock.advance(1000)
        assertTrue(fired.isEmpty())
    }

    @Test
    fun gamepadButtonsAreNeverConsumed() {
        val e = engine(*wayfinderLike)
        assertEquals(false to false, e.tap(SELECT))
        clock.advance(50)
        assertEquals(false to false, e.tap(SELECT))
        assertEquals(listOf(Triple(SELECT, DOUBLE, MOUSE_MODE)), fired)
    }

    @Test
    fun gamepadDoublePressNeedsTwoQuickPresses() {
        val e = engine(*wayfinderLike)
        e.tap(SELECT)
        clock.advance(301)
        e.tap(SELECT)
        assertTrue(fired.isEmpty())
        clock.advance(100)
        e.tap(SELECT) // pairs with the one before
        clock.advance(100)
        e.tap(SELECT) // a third quick press starts a new pair
        assertEquals(1, fired.size)
        clock.advance(100)
        e.tap(SELECT)
        assertEquals(2, fired.size)
    }

    @Test
    fun gamepadHoldFiresWhileTheGameStillSeesTheButton() {
        val e = engine((SELECT to HOLD) to SCREENSHOT)
        assertFalse(e.onKey(SELECT, down = true, repeat = 0, time = 0))
        clock.advance(800)
        assertEquals(listOf(Triple(SELECT, HOLD, SCREENSHOT)), fired)
        assertFalse(e.onKey(SELECT, down = false, repeat = 0, time = clock.now))
    }

    @Test
    fun aQuickPressAfterAGamepadHoldIsNotADoublePress() {
        val e = engine((SELECT to HOLD) to SCREENSHOT, (SELECT to DOUBLE) to MOUSE_MODE)
        e.tap(SELECT, heldMs = 900)
        clock.advance(100)
        e.tap(SELECT)
        assertEquals(listOf(Triple(SELECT, HOLD, SCREENSHOT)), fired)
    }

    @Test
    fun homeAndTheAynButtonAreToldApartByScanCode() {
        assertEquals(PhysicalButton.HOME, PhysicalButton.of(android.view.KeyEvent.KEYCODE_HOME, 102))
        assertEquals(PhysicalButton.AYN, PhysicalButton.of(android.view.KeyEvent.KEYCODE_HOME, 194))
        // injected keys (global actions, on-screen gestures) carry scan code 0
        assertEquals(null, PhysicalButton.of(android.view.KeyEvent.KEYCODE_HOME, 0))
        assertEquals(null, PhysicalButton.of(android.view.KeyEvent.KEYCODE_BACK, 0))
        assertEquals(PhysicalButton.SELECT, PhysicalButton.of(android.view.KeyEvent.KEYCODE_BUTTON_SELECT, 314))
    }
}
