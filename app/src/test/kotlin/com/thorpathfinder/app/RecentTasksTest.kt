package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * activity_recents.txt is `dumpsys activity recents` from an AYN Thor with
 * YouTube and Android Settings open, Pathfinder in the background, Cocoon as
 * the launcher and Launcher3's Recents in the list.
 */
class RecentTasksTest {

    private val captured = javaClass.classLoader!!.getResource("activity_recents.txt")!!.readText()

    private val thor = Exclusions(
        packages = setOf("com.android.launcher3", "rip.moth.cocoonshell", "com.android.systemui"),
        activities = setOf(
            "rip.moth.cocoonshell/rip.moth.cocoonshell.MainActivity",
            "rip.moth.cocoonshell/rip.moth.cocoonshell.ExternalDisplayActivity",
            "com.android.settings/com.android.settings.FallbackHome",
        ),
    )

    @Test
    fun parsesEveryEntryMostRecentFirst() {
        val tasks = RecentTasks.parse(captured)
        assertEquals(listOf(271, 270, 267, 268, 90, 260, 158, 148, 2), tasks.map { it.id })
        assertEquals(
            listOf("standard", "standard", "home", "standard", "recents", "standard", "standard", "standard", "home"),
            tasks.map { it.type },
        )
        assertTrue(tasks.all { it.inRecents })
    }

    @Test
    fun expandsAbbreviatedComponents() {
        val byId = RecentTasks.parse(captured).associateBy { it.id }
        assertEquals("com.android.settings/com.android.settings.homepage.SettingsHomepageActivity", byId[270]!!.component)
        assertEquals("rip.moth.cocoonshell/rip.moth.cocoonshell.ExternalDisplayActivity", byId[260]!!.component)
        assertEquals(
            "com.google.android.youtube/com.google.android.apps.youtube.app.watchwhile.MainActivity",
            byId[271]!!.component,
        )
    }

    @Test
    fun readsIntentFlags() {
        val byId = RecentTasks.parse(captured).associateBy { it.id }
        assertEquals(0x10000000, byId[271]!!.intentFlags)
        assertEquals(0x18800000, byId[260]!!.intentFlags) // excluded from Recents
    }

    @Test
    fun closesWhatClearAllWould() {
        val closing = RecentTasks.closable(RecentTasks.parse(captured), thor)
        // YouTube, Settings and Pathfinder's own window: not the home screens,
        // not Recents itself, not Cocoon's hidden secondary-home tasks.
        assertEquals(listOf(271, 270, 268), closing.map { it.id })
        // Every closed app is stopped except Pathfinder, whose process hosts the service.
        assertEquals(
            listOf("com.google.android.youtube", "com.android.settings"),
            RecentTasks.stoppable(closing, "com.thorpathfinder.app"),
        )
    }

    @Test
    fun anAppOnTheKeepRunningListLosesItsTaskButIsNotStopped() {
        val closing = RecentTasks.closable(RecentTasks.parse(captured), thor)
        val stopping = RecentTasks.stoppable(
            closing,
            "com.thorpathfinder.app",
            keepRunning = setOf("com.google.android.youtube", "org.example.not.open"),
        )
        // Its task is closed with the rest...
        assertTrue(271 in closing.map { it.id })
        // ...but only Settings is force-stopped.
        assertEquals(listOf("com.android.settings"), stopping)
    }

    @Test
    fun aTaskHiddenFromRecentsIsLeftAloneEvenIfNotALauncher() {
        val hidden = listOf(
            RecentTask(50, "standard", "org.example.app/org.example.app.Main", 0x10800000, inRecents = true),
            RecentTask(51, "standard", "org.example.app/org.example.app.Main", 0x10000000, inRecents = false),
            RecentTask(52, "standard", "org.example.app/org.example.app.Main", 0x10000000, inRecents = true),
        )
        assertEquals(listOf(52), RecentTasks.closable(hidden, thor).map { it.id })
    }

    @Test
    fun emptyDumpClosesNothing() {
        assertTrue(RecentTasks.closable(RecentTasks.parse("  Recent tasks:\n"), thor).isEmpty())
    }
}
