package com.thorpathfinder.app

import com.thorpathfinder.app.ButtonAction.NORMAL
import com.thorpathfinder.app.ButtonAction.RECENTS
import com.thorpathfinder.app.ButtonAction.SWAP_SCREENS
import com.thorpathfinder.app.ComboKey.A
import com.thorpathfinder.app.ComboKey.B
import com.thorpathfinder.app.ComboKey.L1
import com.thorpathfinder.app.ComboKey.R1
import com.thorpathfinder.app.Gesture.DOUBLE
import com.thorpathfinder.app.Gesture.HOLD
import com.thorpathfinder.app.Gesture.PRESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A clock that only moves when told to (the same idea as GestureEngineTest's). */
private class ComboClock : Scheduler {
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

private class ComboConfig(
    private val map: Map<Pair<PhysicalButton, Gesture>, ButtonAction>,
    override val combos: Set<Set<ComboKey>>,
) : GestureConfig {
    override fun action(button: PhysicalButton, gesture: Gesture) = map[button to gesture] ?: NORMAL
    override val holdMs = 800L
    override val doubleMs = 300L
}

class ComboTest {

    private val clock = ComboClock()
    private val gestures = mutableListOf<Pair<PhysicalButton, Gesture>>()
    private val combos = mutableListOf<Set<ComboKey>>()

    private fun engine(
        combos: Set<Set<ComboKey>>,
        vararg map: Pair<Pair<PhysicalButton, Gesture>, ButtonAction>,
    ) = GestureEngine(ComboConfig(map.toMap(), combos), clock, fireCombo = { this.combos += it }) { b, g, _ ->
        gestures += b to g
    }

    private fun GestureEngine.down(key: ComboKey) = onKey(key, key.button, down = true, repeat = 0, time = clock.now)
    private fun GestureEngine.up(key: ComboKey) = onKey(key, key.button, down = false, repeat = 0, time = clock.now)

    private val back = ComboKey.BACK
    private val home = ComboKey.HOME
    private val select = ComboKey.SELECT

    @Test
    fun holdingBackAndPressingAIsACombo() {
        val e = engine(setOf(setOf(back, A)))
        assertTrue(e.down(back))
        clock.advance(100)
        assertTrue("A is kept from the app", e.down(A))
        assertEquals(listOf(setOf(back, A)), combos)
        clock.advance(50)
        assertTrue(e.up(A))
        assertTrue(e.up(back))
        clock.advance(1000)
        assertEquals("Back itself does nothing more", emptyList<Pair<PhysicalButton, Gesture>>(), gestures)
    }

    @Test
    fun withNoCombosEveryButtonPassesAsBefore() {
        val e = engine(emptySet(), (PhysicalButton.BACK to DOUBLE) to RECENTS)
        assertTrue(e.down(back))
        assertFalse(e.down(A))
        assertFalse(e.up(A))
        assertTrue(e.up(back))
        clock.advance(400)
        assertEquals(listOf(PhysicalButton.BACK to PRESS), gestures)
        assertEquals(emptyList<Set<ComboKey>>(), combos)
    }

    @Test
    fun aButtonInNoComboStillReachesTheApp() {
        val e = engine(setOf(setOf(back, A)))
        e.down(back)
        assertFalse(e.down(B))
        assertFalse(e.up(B))
        assertTrue(e.down(A))
        assertEquals(listOf(setOf(back, A)), combos)
    }

    @Test
    fun aButtonOnlyInCombosIsStillTakenOverAndGivenBack() {
        val e = engine(setOf(setOf(back, A)))
        assertTrue(e.down(back))
        clock.advance(80)
        assertTrue(e.up(back))
        // No double-press shortcut, so the plain press is handed back at once.
        assertEquals(listOf(PhysicalButton.BACK to PRESS), gestures)
    }

    @Test
    fun theThirdButtonMakesTheBiggerCombo() {
        val e = engine(setOf(setOf(back, L1), setOf(back, L1, R1)))
        e.down(back)
        assertTrue(e.down(L1))
        assertEquals("waits for a third", emptyList<Set<ComboKey>>(), combos)
        clock.advance(60)
        assertTrue(e.down(R1))
        assertEquals(listOf(setOf(back, L1, R1)), combos)
        clock.advance(500)
        assertEquals(1, combos.size)
    }

    @Test
    fun noThirdButtonRunsTheTwoButtonComboAfterTheWait() {
        val e = engine(setOf(setOf(back, L1), setOf(back, L1, R1)))
        e.down(back)
        e.down(L1)
        clock.advance(149)
        assertEquals(emptyList<Set<ComboKey>>(), combos)
        clock.advance(1)
        assertEquals(listOf(setOf(back, L1)), combos)
    }

    @Test
    fun lettingGoRunsTheTwoButtonComboAtOnce() {
        val e = engine(setOf(setOf(back, L1), setOf(back, L1, R1)))
        e.down(back)
        e.down(L1)
        clock.advance(40)
        assertTrue(e.up(L1))
        assertEquals(listOf(setOf(back, L1)), combos)
    }

    @Test
    fun theStartOfAComboIsKeptButRunsNothing() {
        val e = engine(setOf(setOf(back, L1, R1)))
        e.down(back)
        assertTrue(e.down(L1))
        assertTrue(e.up(L1))
        assertTrue("R1 alone can still start it", e.down(R1))
        assertEquals(emptyList<Set<ComboKey>>(), combos)
        assertTrue(e.down(L1))
        assertEquals(listOf(setOf(back, L1, R1)), combos)
    }

    @Test
    fun aComboMustComeBeforeTheHold() {
        val e = engine(setOf(setOf(back, A)), (PhysicalButton.BACK to HOLD) to SWAP_SCREENS)
        e.down(back)
        clock.advance(900)
        assertEquals(listOf(PhysicalButton.BACK to HOLD), gestures)
        assertFalse("after the hold, A goes to the app", e.down(A))
        assertEquals(emptyList<Set<ComboKey>>(), combos)
    }

    @Test
    fun aComboBeforeTheHoldCancelsIt() {
        val e = engine(setOf(setOf(back, A)), (PhysicalButton.BACK to HOLD) to SWAP_SCREENS)
        e.down(back)
        clock.advance(300)
        e.down(A)
        clock.advance(2000)
        e.up(A)
        e.up(back)
        assertEquals(listOf(setOf(back, A)), combos)
        assertEquals(emptyList<Pair<PhysicalButton, Gesture>>(), gestures)
    }

    @Test
    fun theHeldButtonCanRunTheComboAgain() {
        val e = engine(setOf(setOf(back, A)))
        e.down(back)
        e.down(A)
        e.up(A)
        e.down(A)
        e.up(A)
        assertEquals(listOf(setOf(back, A), setOf(back, A)), combos)
    }

    @Test
    fun homeCanBeTheSecondButton() {
        val e = engine(setOf(setOf(back, home)), (PhysicalButton.HOME to PRESS) to RECENTS)
        e.down(back)
        assertTrue(e.down(home))
        assertEquals(listOf(setOf(back, home)), combos)
        assertTrue(e.up(home))
        e.up(back)
        clock.advance(1000)
        assertEquals("Home's own press never ran", emptyList<Pair<PhysicalButton, Gesture>>(), gestures)
    }

    @Test
    fun selectInAComboIsKeptFromTheGame() {
        val e = engine(setOf(setOf(back, select)), (PhysicalButton.SELECT to DOUBLE) to ButtonAction.MOUSE_MODE)
        e.down(back)
        assertTrue(e.down(select))
        assertTrue(e.up(select))
        e.up(back)
        assertEquals(listOf(setOf(back, select)), combos)
        // Select's own double-press still works afterwards, and still reaches the game.
        assertFalse(e.down(select))
        e.up(select)
        clock.advance(100)
        e.down(select)
        assertEquals(listOf(PhysicalButton.SELECT to DOUBLE), gestures)
    }

    @Test
    fun combosAreSetsOfTwoOrThreeWithAButtonToHold() {
        assertTrue(Combos.valid(setOf(back, A)))
        assertTrue(Combos.valid(setOf(back, home, A)))
        assertFalse("no button to hold", Combos.valid(setOf(A, B)))
        assertFalse(Combos.valid(setOf(back)))
        assertFalse(Combos.valid(setOf(back, A, B, L1)))
    }

    @Test
    fun aComboIsStoredAndNamedTheSameWayRoundEitherWay() {
        assertEquals("BACK+A+R1", Combos.id(setOf(R1, A, back)))
        assertEquals(setOf(back, A, R1), Combos.parse("BACK+A+R1"))
        assertEquals("Back + L1 + R1", Combos.label(English, setOf(R1, back, L1)))
        assertNull(Combos.parse("BACK+NOPE"))
        assertNull(Combos.parse("A+B"))
        assertNull(Combos.parse("BACK+BACK"))
    }

    @Test
    fun aButtonsCardListsItsCombos() {
        val all = setOf(setOf(back, L1, R1), setOf(back, A), setOf(home, A))
        assertEquals(listOf(setOf(back, A), setOf(back, L1, R1)), Combos.including(all, back))
        assertEquals(listOf(setOf(home, A)), Combos.including(all, home))
    }

    @Test
    fun thePictureListsEachComboOnceOnItsFirstButtonToHold() {
        val combos = listOf(
            setOf(back, A) to "Screenshot",
            setOf(back, home, B) to "Recent apps",
            setOf(home, ComboKey.X) to "Home",
        )
        assertEquals(listOf("+ A: Screenshot", "+ Home + B: Recent apps"), ButtonMap.comboLines(English, PhysicalButton.BACK, combos))
        assertEquals(listOf("+ X: Home"), ButtonMap.comboLines(English, PhysicalButton.HOME, combos))
        assertEquals(emptyList<String>(), ButtonMap.comboLines(English, PhysicalButton.AYN, combos))
    }

    @Test
    fun aBusyBoxSumsUpTheRest() {
        val combos = listOf(A, B, ComboKey.X, ComboKey.Y).map { setOf(back, it) to "Screenshot" }
        val lines = ButtonMap.comboLines(English, PhysicalButton.BACK, combos)
        assertEquals(ButtonMap.COMBO_LINES, lines.size)
        assertEquals("and 2 more combos", lines.last())
    }

    @Test
    fun xboxStyleIsReadByThePrintedLetters() {
        // Xbox style sends the printed A as BUTTON_B (scan code 0x131), and so on.
        val code = android.view.KeyEvent.KEYCODE_BUTTON_B
        assertEquals(A, ComboKey.of(code, 0x131, xboxStyle = true))
        assertEquals(B, ComboKey.of(android.view.KeyEvent.KEYCODE_BUTTON_A, 0x130, xboxStyle = true))
        assertEquals(ComboKey.X, ComboKey.of(android.view.KeyEvent.KEYCODE_BUTTON_Y, 0x134, xboxStyle = true))
        assertEquals(ComboKey.Y, ComboKey.of(android.view.KeyEvent.KEYCODE_BUTTON_X, 0x133, xboxStyle = true))
        assertEquals(B, ComboKey.of(code, 0x131))
        assertEquals(L1, ComboKey.of(android.view.KeyEvent.KEYCODE_BUTTON_L1, 0x136, xboxStyle = true))
        assertEquals(back, ComboKey.of(android.view.KeyEvent.KEYCODE_BACK, 158, xboxStyle = true))
    }

    @Test
    fun realKeysAreRecognised() {
        assertEquals(A, ComboKey.of(android.view.KeyEvent.KEYCODE_BUTTON_A, 0x130))
        assertEquals(ComboKey.AYN, ComboKey.of(android.view.KeyEvent.KEYCODE_HOME, 194))
        assertEquals(home, ComboKey.of(android.view.KeyEvent.KEYCODE_HOME, 102))
        assertNull("injected keys have no scan code", ComboKey.of(android.view.KeyEvent.KEYCODE_BUTTON_A, 0))
        assertNull(ComboKey.of(android.view.KeyEvent.KEYCODE_VOLUME_UP, 115))
    }
}
