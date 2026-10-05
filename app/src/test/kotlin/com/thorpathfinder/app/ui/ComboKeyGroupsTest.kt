package com.thorpathfinder.app.ui

import com.thorpathfinder.app.ComboKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboKeyGroupsTest {

    /** The D-pad is a hat on the Thor and never reaches the service, so it isn't offered. */
    private val dpad = setOf(ComboKey.UP, ComboKey.DOWN, ComboKey.LEFT, ComboKey.RIGHT)

    @Test
    fun everyButtonButTheDpadHasOneRow() {
        val listed = COMBO_KEY_GROUPS.flatMap { it.second }
        assertEquals(ComboKey.entries.size - dpad.size, listed.size)
        assertEquals(ComboKey.entries.toSet() - dpad, listed.toSet())
    }

    @Test
    fun rowsAreShortEnoughForTheTable() {
        assertTrue(COMBO_KEY_GROUPS.all { it.second.size <= 4 })
    }
}
