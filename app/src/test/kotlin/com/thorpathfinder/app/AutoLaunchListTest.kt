package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoLaunchListTest {

    private val us = "com.thorpathfinder.app"

    @Test
    fun nothingStoredIsAnEmptyList() {
        assertEquals(emptyList<String>(), AutoLaunchList.parse(null))
        assertEquals(emptyList<String>(), AutoLaunchList.parse(""))
    }

    @Test
    fun readsTheListAsTheFrameworkDoes() {
        // The value from issue #1, exactly as stored.
        assertEquals(
            listOf("com.thorwayfinder.app", "james.dsp", us),
            AutoLaunchList.parse("com.thorwayfinder.app,james.dsp,$us"),
        )
    }

    @Test
    fun takesOnlyPathfinderOffAndKeepsTheOrder() {
        assertEquals(
            "com.thorwayfinder.app,james.dsp",
            AutoLaunchList.without("com.thorwayfinder.app,james.dsp,$us", us),
        )
        assertEquals("a.b,c.d", AutoLaunchList.without("a.b,$us,c.d", us))
    }

    @Test
    fun nobodyLeftMeansNoList() {
        assertNull(AutoLaunchList.without(us, us))
        assertNull(AutoLaunchList.without(null, us))
    }

    @Test
    fun leavesAListWithoutPathfinderAlone() {
        assertEquals("a.b,c.d", AutoLaunchList.without("a.b,c.d", us))
    }

    @Test
    fun aSimilarNameIsNotPathfinder() {
        assertEquals("$us.extra", AutoLaunchList.without("$us.extra", us))
    }
}
