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

    /** Root tasks in the order listed: per display, topmost first. */
    fun parse(stackList: String): List<RootTask> {
        val tasks = mutableListOf<RootTask>()
        var id = -1
        var display = -1
        var haveTopTask = true
        for (line in stackList.lineSequence()) {
            val h = header.find(line)
            if (h != null) {
                id = h.groupValues[1].toInt()
                display = h.groupValues[2].toInt()
                haveTopTask = false
                continue
            }
            if (haveTopTask) continue // only a root task's first (top) task counts
            val t = task.find(line) ?: continue
            haveTopTask = true
            val activity = (top.find(line)?.groupValues?.get(1) ?: t.groupValues[2]).takeIf { '/' in it }
            tasks += RootTask(id, display, activity, t.groupValues[3] == "true", taskId = t.groupValues[1].toInt())
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
     * The shell script for [moves]. When one app is sent across, the screen it
     * left goes to its home screen at once. Otherwise Android shows whatever is
     * next underneath, and that is rarely what the screen showed before:
     * Cocoon closes its home activity while an app covers it, so the next thing
     * down can be Launcher3's Recents, or an app the user had already sent home.
     * Home is pressed after the move, never before, since pressing it first
     * would briefly cover the moving app and make a video stutter.
     */
    fun script(moves: List<Move>): String {
        val move = moves.joinToString(" && ") { "am display move-stack ${it.task} ${it.to}" }
        val left = moves.singleOrNull()?.from ?: return move
        return "$move && (input -d $left keyevent KEYCODE_HOME || true)"
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

    /** Swaps the two screens' apps. Blocking: run off the main thread. */
    fun swap(context: Context): Outcome {
        if (!Shell.ready) return Outcome.NeedsShizuku
        val displays = context.getSystemService(DisplayManager::class.java).displays
        val main = Display.DEFAULT_DISPLAY
        val other = displays.filter { it.displayId != main }.minByOrNull { it.displayId }
            ?: return Outcome.NoSecondScreen
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

        val result = Shell.sh(script(moves))
        if (!result.ok) {
            return Outcome.Failed(result.err.lineSequence().firstOrNull { it.isNotBlank() } ?: "move failed")
        }
        val outcome = if (moves.size == 2) Outcome.Swapped else Outcome.Moved

        // Launchers react to screens changing: Cocoon relaunches its bottom-screen
        // home when it finds it gone, and that can land on top of the app that
        // just arrived. Give them a moment, then put things right.
        Thread.sleep(SETTLE_MS)
        val after = Shell.run("am", "stack", "list").takeIf { it.ok }?.let { parse(it.out) } ?: return outcome
        for (move in covered(after, moves)) {
            val task = after.first { it.id == move.task }
            Log.i(TAG, "moved app $task was covered on screen ${move.to}; bringing it to the front")
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
