package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutMenuTest {

    @Test
    fun theMenuLeavesOutWhatNeedsAButtonAndItself() {
        val menu = ButtonAction.menuChoices
        assertFalse(ButtonAction.NORMAL in menu)
        assertFalse(ButtonAction.NOTHING in menu)
        assertFalse(ButtonAction.SHORTCUT_MENU in menu)
    }

    @Test
    fun theMenuOffersEveryOtherShortcut() {
        val expected = ButtonAction.entries.toSet() -
            setOf(ButtonAction.NORMAL, ButtonAction.NOTHING, ButtonAction.SHORTCUT_MENU)
        assertEquals(expected, ButtonAction.menuChoices.toSet())
    }

    @Test
    fun everyButtonCanOpenTheMenu() {
        for (button in PhysicalButton.entries) {
            assertTrue(ButtonAction.SHORTCUT_MENU in ButtonAction.choicesFor(button))
        }
    }

    @Test
    fun aMenuPickCarriesItsChoices() {
        // What the menu hands the service: the action with only the choices it made.
        val home = Shortcut(ButtonAction.HOME, home = HomeTarget.BOTTOM)
        assertEquals(HomeTarget.BOTTOM, home.home)
        assertEquals(null, home.app)
        assertEquals(FocusSwitch.CYCLE, Shortcut(ButtonAction.FOCUS_MODE).focus)
    }
}
