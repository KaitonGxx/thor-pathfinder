package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProfilesTest {

    private val own = "com.thorpathfinder.app"

    // Root tasks as the helper read them on the test Thor: Pathfinder on the
    // top screen (display 0), Cocoon's second home on the bottom (display 4).
    private val pathfinder = TaskEntry(0, true, TaskList.TYPE_STANDARD, "$own/$own.ui.MainActivity")
    private val cocoonTop = TaskEntry(0, false, TaskList.TYPE_HOME, "rip.moth.cocoonshell/rip.moth.cocoonshell.MainActivity")
    private val recents = TaskEntry(0, false, TaskList.TYPE_RECENTS, "com.android.launcher3/com.android.quickstep.RecentsActivity")
    private val placeholder = TaskEntry(0, false, 0, null)
    private val cocoonBottom = TaskEntry(
        4, true, TaskList.TYPE_HOME, "rip.moth.cocoonshell/rip.moth.cocoonshell.ExternalDisplayActivity",
    )
    private val tasks = listOf(pathfinder, cocoonTop, recents, placeholder, cocoonBottom)

    @Test
    fun aReportLineReadsBack() {
        val line = TaskList.line(4, true, TaskList.TYPE_HOME, cocoonBottom.component)
        assertEquals("4|true|2|rip.moth.cocoonshell/rip.moth.cocoonshell.ExternalDisplayActivity", line)
        assertEquals(cocoonBottom, TaskList.parse(line))
        assertEquals(placeholder, TaskList.parse(TaskList.line(0, false, 0, null)))
    }

    @Test
    fun aBrokenLineIsNothing() {
        assertNull(TaskList.parse(null))
        assertNull(TaskList.parse(""))
        assertNull(TaskList.parse("x|true|1|a/b"))
        assertNull(TaskList.parse("0|true|1"))
    }

    @Test
    fun focusLockedToAScreenMeansThatScreensApp() {
        assertEquals(pathfinder, AppProfiles.controllerApp(cocoonBottom, tasks, FocusMode.TOP, 4))
        assertEquals(cocoonBottom, AppProfiles.controllerApp(pathfinder, tasks, FocusMode.BOTTOM, 4))
    }

    @Test
    fun autoLockFollowsTheFocusedApp() {
        assertEquals(cocoonBottom, AppProfiles.controllerApp(cocoonBottom, tasks, FocusMode.AUTO, 4))
        assertEquals(pathfinder, AppProfiles.controllerApp(pathfinder, tasks, FocusMode.AUTO, 4))
    }

    @Test
    fun noBottomScreenMeansNoAnswer() {
        assertNull(AppProfiles.controllerApp(pathfinder, tasks, FocusMode.BOTTOM, null))
    }

    @Test
    fun onlyAVisibleTaskWithAnActivityShows() {
        val hidden = listOf(cocoonTop, placeholder, pathfinder)
        assertEquals(pathfinder, AppProfiles.shownOn(hidden, 0))
        assertNull(AppProfiles.shownOn(listOf(cocoonTop, placeholder), 0))
    }

    @Test
    fun recentsAndPathfindersQuestionsOnlyPassOver() {
        assertTrue(AppProfiles.passing(recents, own))
        assertTrue(AppProfiles.passing(placeholder, own))
        for (question in AppProfiles.QUESTIONS) {
            assertTrue(question, AppProfiles.passing(TaskEntry(0, true, TaskList.TYPE_STANDARD, "$own/$question"), own))
        }
    }

    @Test
    fun appsAndHomeScreensCount() {
        assertFalse(AppProfiles.passing(pathfinder, own))
        assertFalse(AppProfiles.passing(cocoonBottom, own))
        assertFalse(AppProfiles.passing(TaskEntry(0, true, TaskList.TYPE_STANDARD, "com.retroarch/.Main"), own))
    }

    @Test
    fun theMessageNamesTheAppWhenThereIsOne() {
        assertEquals("Profile: Emulators (RetroArch)", AppProfiles.switchMessage(English, "Emulators", "RetroArch"))
        assertEquals("Profile: Main profile", AppProfiles.switchMessage(English, "Main profile", null))
    }

    private val linked = mapOf("com.retroarch" to 2, "org.dolphinemu" to 2, "com.discord" to 3)

    @Test
    fun aLinkedAppUsesItsProfile() {
        assertEquals(2, Profiles.inUse(0, "com.retroarch", null) { linked[it] })
        assertEquals(3, Profiles.inUse(0, "com.discord", null) { linked[it] })
    }

    @Test
    fun anythingElseUsesTheChosenProfile() {
        assertEquals(0, Profiles.inUse(0, "com.android.chrome", null) { linked[it] })
        assertEquals(0, Profiles.inUse(0, null, null) { linked[it] })
    }

    @Test
    fun aSwitchMadeInAnAppHoldsThere() {
        assertEquals(5, Profiles.inUse(5, "com.retroarch", "com.retroarch") { linked[it] })
        // Held in another app, which has since been left: this one's link applies again.
        assertEquals(2, Profiles.inUse(5, "com.retroarch", "com.discord") { linked[it] })
    }

    @Test
    fun linkingMovesAnAppFromItsOldProfile() {
        val before = mapOf(2 to setOf("com.retroarch", "org.dolphinemu"), 3 to setOf("com.discord"))
        val after = Profiles.relinked(before, 3, setOf("com.discord", "com.retroarch"))
        assertEquals(mapOf(2 to setOf("org.dolphinemu"), 3 to setOf("com.discord", "com.retroarch")), after)
    }

    @Test
    fun aProfileLeftWithNoAppsDropsOut() {
        val before = mapOf(2 to setOf("com.retroarch"))
        assertEquals(mapOf(4 to setOf("com.retroarch")), Profiles.relinked(before, 4, setOf("com.retroarch")))
        assertEquals(emptyMap<Int, Set<String>>(), Profiles.relinked(before, 2, emptySet()))
    }
}
