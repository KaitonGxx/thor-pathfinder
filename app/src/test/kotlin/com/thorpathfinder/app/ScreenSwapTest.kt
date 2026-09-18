package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** stack_list_two_apps.txt is `am stack list` from an AYN Thor: Settings on top, Wayfinder below. */
class ScreenSwapTest {

    private val captured = javaClass.classLoader!!.getResource("stack_list_two_apps.txt")!!.readText()

    // What the Thor's home screens resolve to: real launchers by package,
    // Android Settings' FallbackHome (priority -1000) by activity only.
    private val thor = Exclusions(
        packages = setOf("com.android.launcher3", "rip.moth.cocoonshell"),
        activities = setOf("com.android.settings/com.android.settings.FallbackHome"),
    )

    @Test
    fun parsesEachRootTaskWithItsTopActivity() {
        assertEquals(
            listOf(
                RootTask(93, 0, "com.android.settings/com.android.settings.Settings", visible = true, taskId = 93),
                RootTask(1, 0, "rip.moth.cocoonshell/rip.moth.cocoonshell.MainActivity", visible = false, taskId = 83),
                RootTask(89, 0, "com.android.launcher3/com.android.quickstep.RecentsActivity", visible = false, taskId = 90),
                RootTask(3, 0, null, visible = false, taskId = 4),
                RootTask(92, 4, "com.thorwayfinder.app/com.thorwayfinder.app.MainActivity", visible = true, taskId = 92),
            ),
            ScreenSwap.parse(captured),
        )
    }

    @Test
    fun twoAppsSwap() {
        assertEquals(listOf(Move(93, 0, 4), Move(92, 4, 0)), ScreenSwap.plan(ScreenSwap.parse(captured), 0, 4, thor))
    }

    @Test
    fun aLoneAppIsSentAcross() {
        val tasks = listOf(
            RootTask(7, 0, "rip.moth.cocoonshell/rip.moth.cocoonshell.MainActivity", visible = true),
            RootTask(8, 4, "org.ppsspp.ppsspp/org.ppsspp.ppsspp.PpssppActivity", visible = true),
        )
        assertEquals(listOf(Move(8, 4, 0)), ScreenSwap.plan(tasks, 0, 4, thor))
    }

    @Test
    fun homeScreensAndHiddenAppsNeverMove() {
        val tasks = listOf(
            RootTask(1, 0, "rip.moth.cocoonshell/rip.moth.cocoonshell.MainActivity", visible = true),
            RootTask(5, 0, "org.ppsspp.ppsspp/org.ppsspp.ppsspp.PpssppActivity", visible = false),
            RootTask(9, 4, "com.android.settings/com.android.settings.FallbackHome", visible = true),
        )
        assertTrue(ScreenSwap.plan(tasks, 0, 4, thor).isEmpty())
    }

    @Test
    fun theTopmostVisibleAppOnAScreenIsTheOneThatMoves() {
        val tasks = listOf(
            RootTask(20, 4, "com.android.settings/com.android.settings.Settings", visible = true),
            RootTask(21, 4, "org.ppsspp.ppsspp/org.ppsspp.ppsspp.PpssppActivity", visible = true),
        )
        assertEquals(listOf(Move(20, 4, 0)), ScreenSwap.plan(tasks, 0, 4, thor))
    }

    @Test
    fun aSwapIsJustTheTwoMoves() {
        assertEquals(
            "am display move-stack 93 4 && am display move-stack 92 0",
            ScreenSwap.script(listOf(Move(93, 0, 4), Move(92, 4, 0))),
        )
    }

    @Test
    fun theScreenALoneAppLeavesGoesHomeStraightAfter() {
        assertEquals(
            "am display move-stack 115 4 && (input -d 0 keyevent KEYCODE_HOME || true)",
            ScreenSwap.script(listOf(Move(115, 0, 4))),
        )
    }

    @Test
    fun anAppSentHomeEarlierStaysHidden() {
        // Reported on the Thor: YouTube on top, Discord on the bottom, Home
        // pressed on the bottom screen. Swap sends YouTube down over the home
        // screen, and Cocoon closes that home while it is covered. Swapping
        // back must not reveal Discord, so the bottom screen is sent home.
        val youtubeBelow = listOf(
            RootTask(40, 0, "rip.moth.cocoonshell/rip.moth.cocoonshell.MainActivity", visible = true),
            RootTask(41, 4, "com.google.android.youtube/com.google.android.youtube.HomeActivity", visible = true),
            RootTask(42, 4, "com.discord/com.discord.main.MainActivity", visible = false),
        )
        val moves = ScreenSwap.plan(youtubeBelow, 0, 4, thor)
        assertEquals(listOf(Move(41, 4, 0)), moves)
        assertTrue(ScreenSwap.script(moves).endsWith("input -d 4 keyevent KEYCODE_HOME || true)"))
    }

    @Test
    fun aMovedAppThatALauncherLandedOnIsSpotted() {
        // Seen on the Thor: Settings sent to an empty bottom screen, and Cocoon
        // relaunched its bottom-screen home on top of it moments later.
        val after = listOf(
            RootTask(118, 4, "rip.moth.cocoonshell/rip.moth.cocoonshell.ExternalDisplayActivity", visible = true),
            RootTask(115, 4, "com.android.settings/com.android.settings.Settings", visible = false),
            RootTask(116, 0, "rip.moth.cocoonshell/rip.moth.cocoonshell.MainActivity", visible = true),
        )
        val move = Move(115, 0, 4)
        assertEquals(listOf(move), ScreenSwap.covered(after, listOf(move)))
        val fine = listOf(after[1].copy(visible = true), after[0].copy(visible = false), after[2])
        assertTrue(ScreenSwap.covered(fine, listOf(move)).isEmpty())
    }

    // Shaped like Android 13's `dumpsys media_session`: one block per session.
    private val sessions = """
        |MEDIA SESSION SERVICE (dumpsys media_session)
        |  Sessions Stack - have 2 sessions:
        |    YouTube com.google.android.youtube/YouTube (userId=0)
        |      ownerPid=4711, ownerUid=10150, userId=0
        |      package=com.google.android.youtube
        |      active=true
        |      state=PlaybackState {state=3, position=81234, buffered position=0, speed=1.0, updated=1, actions=823, custom actions=[], active item id=-1, error=null}
        |    Spotify com.spotify.music/spotify-media-session (userId=0)
        |      package=com.spotify.music
        |      active=false
        |      state=PlaybackState {state=2, position=0, buffered position=0, speed=0.0, updated=1, actions=0, custom actions=[], active item id=-1, error=null}
        |""".trimMargin()

    @Test
    fun readsWhichAppsArePlaying() {
        assertEquals(setOf("com.google.android.youtube"), ScreenSwap.playingPackages(sessions))
        assertEquals(
            setOf("org.videolan.vlc"),
            ScreenSwap.playingPackages(
                listOf(
                    "      package=org.videolan.vlc",
                    "      state=PlaybackState {state=PLAYING(3), position=0}",
                ).joinToString("\n")
            ),
        )
        assertTrue(ScreenSwap.playingPackages("  Sessions Stack - have 0 sessions:").isEmpty())
    }

    @Test
    fun aPlayingVideoMovesFirstSoItIsNeverCovered() {
        val tasks = listOf(
            RootTask(30, 0, "org.ppsspp.ppsspp/org.ppsspp.ppsspp.PpssppActivity", visible = true),
            RootTask(31, 4, "com.google.android.youtube/com.google.android.apps.youtube.app.watchwhile.MainActivity", visible = true),
        )
        assertEquals(
            listOf(Move(31, 4, 0), Move(30, 0, 4)),
            ScreenSwap.plan(tasks, 0, 4, thor, playing = setOf("com.google.android.youtube")),
        )
        // nothing playing: the top screen's app goes first
        assertEquals(listOf(Move(30, 0, 4), Move(31, 4, 0)), ScreenSwap.plan(tasks, 0, 4, thor))
    }
}
