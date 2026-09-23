package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonMapTest {

    @Test
    fun everyButtonThatCanCarryAShortcutHasAPlaceOnThePicture() {
        // A button added later must be given a spot, or it would be mapped
        // in the settings and missing from the map.
        assertEquals(PhysicalButton.entries.toSet(), ButtonMap.SPOTS.keys)
    }

    @Test
    fun theTwoSidesAreBothUsed() {
        // Boxes stack down whichever side their button is on, so neither
        // side should end up with all of them.
        val sides = ButtonMap.SPOTS.values.map { it.side }.toSet()
        assertEquals(ButtonMap.Side.entries.toSet(), sides)
    }

    @Test
    fun everySpotIsOnThePicture() {
        assertTrue(ButtonMap.SPOTS.values.all { it.x in 0f..1f && it.y in 0f..1f })
        // The deck is the lower half, and every mappable button is on it.
        assertTrue(ButtonMap.SPOTS.values.all { it.y > 0.5f })
    }

    @Test
    fun aButtonWithNothingOnItGetsNoBox() {
        assertNull(ButtonMap.callout(PhysicalButton.START, emptyList()))
    }

    @Test
    fun aBoxNamesTheButtonAndSaysWhatEachGestureDoes() {
        val callout = ButtonMap.callout(
            PhysicalButton.BACK,
            listOf(Gesture.DOUBLE to "Recent apps", Gesture.HOLD to "Swap screens"),
        )
        assertEquals("Back", callout!!.title)
        assertEquals(listOf("Double-press: Recent apps", "Hold: Swap screens"), callout.lines)
    }
}
