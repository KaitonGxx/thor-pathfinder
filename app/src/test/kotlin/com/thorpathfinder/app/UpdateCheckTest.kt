package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** release_latest.json follows GitHub's answer to /releases/latest, trimmed. */
class UpdateCheckTest {

    private val latest = javaClass.classLoader!!.getResource("release_latest.json")!!.readText()

    @Test
    fun readsTheVersionAndTheReleasePage() {
        assertEquals(
            UpdateCheck.Release("0.3.0", "https://github.com/KaitonGxx/thor-pathfinder/releases/tag/v0.3.0"),
            UpdateCheck.parse(latest),
        )
    }

    @Test
    fun onlyOpensPagesOnGitHub() {
        val elsewhere = """{"tag_name": "v1.0.0", "html_url": "https://example.com/pathfinder"}"""
        assertEquals(UpdateCheck.LATEST_PAGE, UpdateCheck.parse(elsewhere)?.page)
        assertEquals(UpdateCheck.LATEST_PAGE, UpdateCheck.parse("""{"tag_name": "1.0.0"}""")?.page)
    }

    @Test
    fun anythingElseIsNoRelease() {
        assertNull(UpdateCheck.parse("""{"message": "Not Found"}"""))
        assertNull(UpdateCheck.parse("<html>rate limited</html>"))
        assertNull(UpdateCheck.parse(""))
    }

    @Test
    fun comparesVersionsNumberByNumber() {
        assertTrue(UpdateCheck.isNewer("0.4.0", "0.3.0"))
        assertTrue(UpdateCheck.isNewer("0.10.0", "0.9.2"))
        assertTrue(UpdateCheck.isNewer("1.0", "0.99.99"))
        assertTrue(UpdateCheck.isNewer("0.3.1", "0.3"))
        assertFalse(UpdateCheck.isNewer("0.3.0", "0.3.0"))
        assertFalse(UpdateCheck.isNewer("0.3", "0.3.0"))
        assertFalse(UpdateCheck.isNewer("0.3.0", "0.4.0"))
        assertFalse(UpdateCheck.isNewer("v0.3.0", "0.3.0"))
        assertFalse(UpdateCheck.isNewer("0.4.0-beta", "0.4.0"))
    }
}
