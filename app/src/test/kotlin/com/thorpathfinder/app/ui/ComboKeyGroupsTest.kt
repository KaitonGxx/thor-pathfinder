package com.thorpathfinder.app.ui

import com.thorpathfinder.app.ComboKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboKeyGroupsTest {

    @Test
    fun everyButtonHasOneRow() {
        val listed = COMBO_KEY_GROUPS.flatMap { it.second }
        assertEquals(ComboKey.entries.size, listed.size)
        assertEquals(ComboKey.entries.toSet(), listed.toSet())
    }

    @Test
    fun rowsAreShortEnoughForTheTable() {
        assertTrue(COMBO_KEY_GROUPS.all { it.second.size <= 4 })
    }
}
