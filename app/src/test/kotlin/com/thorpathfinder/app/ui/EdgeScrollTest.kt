package com.thorpathfinder.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A page of 3000 px shown in a 1000 px viewport (max scroll 2000), with the
 * edge reach at 150 px. USUAL stands for Compose's own answer.
 */
class EdgeScrollTest {

    private fun distance(value: Float, offset: Float, size: Float = 100f) =
        edgeAwareScrollDistance(
            value = value,
            max = 2000f,
            offset = offset,
            size = size,
            viewport = 1000f,
            edge = 150f,
        ) { USUAL }

    @Test
    fun theFirstItemTakesTheScrollToTheVeryTop() {
        // Button at 40..140 in the content, page scrolled to 300: it sits above the viewport.
        assertEquals(-300f, distance(value = 300f, offset = -260f), 0.01f)
        // Already at the top: nothing to do.
        assertEquals(0f, distance(value = 0f, offset = 40f), 0.01f)
    }

    @Test
    fun theLastItemTakesTheScrollToTheVeryEnd() {
        // Item at 2820..2920 in the content (80 px above the end), page scrolled to 1900.
        assertEquals(100f, distance(value = 1900f, offset = 920f), 0.01f)
        assertEquals(0f, distance(value = 2000f, offset = 820f), 0.01f)
    }

    @Test
    fun itemsInTheMiddleScrollTheUsualWay() {
        assertEquals(USUAL, distance(value = 500f, offset = 1100f), 0.01f)
        // 200 px from the top is beyond the reach.
        assertEquals(USUAL, distance(value = 300f, offset = -100f), 0.01f)
    }

    @Test
    fun anItemThatWouldNotFitAtTheEndScrollsTheUsualWay() {
        // Tall item starting near the top but ending below the first screen.
        assertEquals(USUAL, distance(value = 300f, offset = -250f, size = 1200f), 0.01f)
        // Tall item ending near the bottom but starting before the last screen.
        assertEquals(USUAL, distance(value = 1500f, offset = 300f, size = 1150f), 0.01f)
    }

    private companion object {
        const val USUAL = 12345f
    }
}
