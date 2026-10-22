package com.thorpathfinder.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display

/** A root task (window stack) as `am stack list` prints it. */
data class RootTask(
    val id: Int,
    val displayId: Int,
    /** "package/class" of the task's top activity, or null for placeholder tasks. */
    val topActivity: String?,
    val visible: Boolean,
    /** The root task's top task; for an ordinary app it is the root task itself. */
    val taskId: Int = id,
    /** The root task's `mActivityType`: standard, home, recents, or undefined. */
    val activityType: String? = null,
) {
    val topPackage: String? get() = topActivity?.substringBefore('/')
}

/**
 * Launchers never move: their packages, plus single home activities of other
 * apps. [activities] holds every home and secondary-home activity.
 */
data class Exclusions(val packages: Set<String>, val activities: Set<String>) {
    fun excludes(task: RootTask): Boolean =
        task.topPackage in packages || task.topActivity in activities
}

/** Root task [task] goes from display [from] to display [to]. */
data class Move(val task: Int, val from: Int, val to: Int)

/**
 * Moving apps between the Thor's screens.
 *
 * `am display move-stack` hands a running app's whole root task to the other
 * display, so the app keeps its state instead of being relaunched there.
 */
object ScreenSwap {

    private const val TAG = "PathfinderSwap"
    private val header = Regex("""^(?:RootTask|Stack) id=(\d+)\b.*\bdisplayId=(\d+)""")
    private val task = Regex("""^\s+taskId=(\d+): (\S+)\s.*\bvisible=(true|false)""")
    private val top = Regex("""\btopActivity=ComponentInfo\{([^}]+)\}""")
    private val activityType = Regex("""\bmActivityType=(\w+)""")

    /** Root tasks in the order listed: per display, topmost first. */
    fun parse(stackList: String): List<RootTask> {
        val tasks = mutableListOf<RootTask>()
        var id = -1
        var display = -1
        var type: String? = null
        var haveTopTask = true
        for (line in stackList.lineSequence()) {
            val h = header.find(line)
            if (h != null) {
                id = h.groupValues[1].toInt()
                display = h.groupValues[2].toInt()
                type = null
                haveTopTask = false
                continue
            }
            if (haveTopTask) continue // only a root task's first (top) task counts
            // The configuration line between the header and the tasks names the root task's type.
            val a = if (type == null) activityType.find(line) else null
            if (a != null) {
                type = a.groupValues[1]
                continue
            }
            val t = task.find(line) ?: continue
            haveTopTask = true
            val activity = (top.find(line)?.groupValues?.get(1) ?: t.groupValues[2]).takeIf { '/' in it }
            tasks += RootTask(id, display, activity, t.groupValues[3] == "true", taskId = t.groupValues[1].toInt(), activityType = type)
        }
        return tasks
    }

    /** The app the user sees on [displayId], if any. */
    fun visibleApp(tasks: List<RootTask>, displayId: Int, exclusions: Exclusions): RootTask? =
        tasks.firstOrNull {
            it.displayId == displayId && it.visible && it.topActivity != null && !exclusions.excludes(it)
        }

    /**
     * Swap both screens' apps, or send the one there is across.
     *
     * A swap is two moves in a row, and the first app to move lands on top of
     * the second for a few milliseconds before that one leaves. Even that is
     * enough for Android to hide the second app, and a video or game drops its
     * picture and stutters. So the first to move is an app playing media, if
     * one is, and otherwise the top screen's, where games usually run.
     */
    fun plan(
        tasks: List<RootTask>,
        first: Int,
        second: Int,
        exclusions: Exclusions,
        playing: Set<String> = emptySet(),
    ): List<Move> {
        val a = visibleApp(tasks, first, exclusions)
        val b = visibleApp(tasks, second, exclusions)
        val moves = listOfNotNull(a?.let { Move(it.id, first, second) }, b?.let { Move(it.id, second, first) })
        val bFirst = a != null && b != null && b.topPackage in playing && a.topPackage !in playing
        return if (bFirst) moves.reversed() else moves
    }

    private val sessionPackage = Regex("""^\s*package=(\S+)""")
    private val sessionState = Regex("""state=PlaybackState \{state=(?:[A-Z_]+\()?(\d+)""")

    /** Packages whose media session is playing or buffering, from `dumpsys media_session`. */
    fun playingPackages(dump: String): Set<String> {
        val playing = mutableSetOf<String>()
        var pkg: String? = null
        for (line in dump.lineSequence()) {
            sessionPackage.find(line)?.let { pkg = it.groupValues[1] }
            val state = sessionState.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (state == STATE_PLAYING || state == STATE_BUFFERING) pkg?.let(playing::add)
        }
        return playing
    }

    // PlaybackState.STATE_PLAYING and STATE_BUFFERING
    private const val STATE_PLAYING = 3
    private const val STATE_BUFFERING = 6

    /**
     * The screen to press Home on after [moves], if any. When one app is sent
     * across, Android shows whatever is next underneath on the screen it left,
     * and that is often not what the screen showed before: Cocoon closes its
     * home activity while an app covers it, so the next thing down can be
     * Launcher3's Recents, or an app the user had already sent home. Then the
     * screen goes home. But when a home screen is still next underneath, it is
     * left to show: Home would start the system's secondary home over it (not
     * the launcher the user put there), and that start takes input focus to
     * the screen the app left, so Back went nowhere (issue #4).
     */
    fun homeAfter(tasks: List<RootTask>, moves: List<Move>, exclusions: Exclusions): Int? {
        val move = moves.singleOrNull() ?: return null
        val below = tasks.filter { it.displayId == move.from && it.topActivity != null }
            .dropWhile { it.id != move.task }.drop(1).firstOrNull()
        val home = below != null && (below.activityType == "home" || below.topActivity in exclusions.activities)
        return if (home) null else move.from
    }

    /**
     * The shell script for [moves], then Home on screen [home] (see [homeAfter]).
     * Home is pressed after the move, never before, since pressing it first
     * would briefly cover the moving app and make a video stutter.
     */
    fun script(moves: List<Move>, volumes: ScreenVolume.Levels? = null, home: Int? = null): String {
        val move = moves.joinToString(" && ") { "am display move-stack ${it.task} ${it.to}" }
        val moved = if (home == null) move else "$move && (input -d $home keyevent KEYCODE_HOME || true)"
        // The volume changes start first, in the background, and finish while the apps move.
        // The exit status is the moves': `wait` alone would hide a failed one.
        return if (volumes == null) moved else "${ScreenVolume.commands(volumes)}$moved; r=$?; wait; exit \$r"
    }

    sealed interface Outcome {
        data object Swapped : Outcome
        data object Moved : Outcome
        data object NothingToMove : Outcome
        data object NoSecondScreen : Outcome
        data object SecondScreenOff : Outcome
        data object NeedsShizuku : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** The Thor's other screen: the lowest display that isn't the default one. */
    fun otherDisplay(context: Context): Display? =
        context.getSystemService(DisplayManager::class.java).displays
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
            .minByOrNull { it.displayId }

    /** Swaps the two screens' apps. Blocking: run off the main thread. */
    fun swap(context: Context, quick: ((List<Move>, ScreenVolume.Levels) -> Boolean)? = null): Outcome {
        if (!Shell.ready) return Outcome.NeedsShizuku
        val main = Display.DEFAULT_DISPLAY
        val other = otherDisplay(context) ?: return Outcome.NoSecondScreen
        if (other.state == Display.STATE_OFF) return Outcome.SecondScreenOff

        val list = Shell.run("am", "stack", "list")
        if (!list.ok) return Outcome.Failed(list.err.ifBlank { "am stack list failed" })
        val tasks = parse(list.out)
        val exclusions = exclusions(context)
        val sessions = Shell.run("dumpsys", "media_session")
        val playing = if (sessions.ok) playingPackages(sessions.out) else emptySet()
        val moves = plan(tasks, main, other.displayId, exclusions, playing)
        // One line per swap, so a bug report's logcat shows what Pathfinder saw.
        Log.i(TAG, "screens $main+${other.displayId}; visible ${tasks.filter { it.visible }}; playing $playing; moves $moves")
        if (moves.isEmpty()) return Outcome.NothingToMove
        val home = homeAfter(tasks, moves, exclusions)
        if (moves.size == 1) Log.i(TAG, if (home == null) "a home screen is underneath; no Home" else "Home on screen $home")

        // Read before the move, and written in the same command as it, so the volumes follow the apps at once.
        val volumes = if (ScreenVolume.enabled(context)) ScreenVolume.read() else null
        // With volumes to swap, the helper does it all in-process: shell commands each took ~50 ms,
        // and the apps were heard at the wrong volume in between. The shell route is the fallback.
        val result = if (volumes != null && quick?.invoke(moves, volumes) == true) {
            Log.i(TAG, "swapped in the helper, volumes $volumes")
            home?.let { Shell.sh("input -d $it keyevent KEYCODE_HOME || true") } ?: Shell.Result(0, "", "")
        } else {
            Shell.sh(script(moves, volumes, home))
        }
        if (!result.ok) {
            return Outcome.Failed(result.err.lineSequence().firstOrNull { it.isNotBlank() } ?: "move failed")
        }
        val outcome = if (moves.size == 2) Outcome.Swapped else Outcome.Moved

        // Launchers react to screens changing: Cocoon relaunches its bottom-screen
        // home when it finds it gone, and that can land on top of the app that
        // just arrived. Give them a moment, then put things right.
        Thread.sleep(SETTLE_MS)
        val after = Shell.run("am", "stack", "list").takeIf { it.ok }?.let { parse(it.out) } ?: return outcome
        for (move in toFront(after, moves, home)) {
            val task = after.first { it.id == move.task }
            Log.i(TAG, "bringing moved app $task to the front on screen ${move.to}")
            context.getSystemService(ActivityManager::class.java).moveTaskToFront(task.taskId, 0)
        }
        return outcome
    }

    private const val SETTLE_MS = 300L

    /** Moves whose app ended up under something else on its new screen. */
    fun covered(after: List<RootTask>, moves: List<Move>): List<Move> = moves.filter { move ->
        val top = after.firstOrNull { it.displayId == move.to && it.visible && it.topActivity != null }
        top != null && top.id != move.task && after.any { it.id == move.task && it.displayId == move.to }
    }

    /**
     * Moves whose app goes to the front again: a [covered] one, and a lone
     * app whose old screen was sent [home], since starting that home took
     * input focus to the old screen and Back would have gone there.
     */
    fun toFront(after: List<RootTask>, moves: List<Move>, home: Int?): List<Move> {
        val refocus = moves.filter { move -> home == move.from && after.any { it.id == move.task && it.displayId == move.to } }
        return (covered(after, moves) + refocus).distinct()
    }

    /**
     * Home screens stay put. Real launchers are excluded whole; an app that
     * only offers a fallback home (Android Settings' FallbackHome, priority
     * below zero) is excluded by that one activity, so Settings itself still moves.
     */
    fun exclusions(context: Context): Exclusions {
        val packages = mutableSetOf("com.android.systemui")
        packages += AYN_HELPERS
        val activities = mutableSetOf<String>()
        for (category in listOf(Intent.CATEGORY_HOME, Intent.CATEGORY_SECONDARY_HOME)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in context.packageManager.queryIntentActivities(intent, 0)) {
                val activity = info.activityInfo ?: continue
                activities += "${activity.packageName}/${activity.name}"
                if (info.priority >= 0) packages += activity.packageName
            }
        }
        return Exclusions(packages, activities)
    }

    /** AYN's own overlay helpers, which are not apps anyone wants moved. */
    private val AYN_HELPERS = setOf("com.odin.gameassistant", "com.odin.dualscreen.assistant")
}
