package com.thorpathfinder.app

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log

/** One entry of `dumpsys activity recents`. */
data class RecentTask(
    val id: Int,
    /** "standard", "home", "recents", ... as the dump names it. */
    val type: String,
    /** "package/fully.qualified.Activity", or null when the dump has none. */
    val component: String?,
    val intentFlags: Int,
    val inRecents: Boolean,
) {
    val packageName: String? get() = component?.substringBefore('/')
}

/**
 * Closing every app, the way Recents' "Clear all" does.
 *
 * Clear all removes each task shown in Recents and stops its app.
 * `am stack remove <task>` (through Shizuku; the shell user holds
 * REMOVE_TASKS) removes one task, and works for tasks that are only in
 * Recents, no longer on a screen; `am force-stop` then ends the app. The
 * tasks come from `dumpsys activity recents`: standard tasks still in
 * Recents, minus home screens and tasks that hide themselves from Recents.
 * Pathfinder's own window goes too, so Recents ends up empty; only its
 * process is spared, because the accessibility service runs in it.
 */
object RecentTasks {

    private const val TAG = "PathfinderClose"

    // Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
    private const val EXCLUDE_FROM_RECENTS = 0x00800000

    private val header = Regex("""^\s*\* Recent #\d+: Task\{\S+ #(\d+) type=(\w+)""")
    private val component = Regex("""^\s*mActivityComponent=(\S+)""")
    private val flags = Regex("""\bflg=0x([0-9a-fA-F]+)""")
    private val inRecents = Regex("""\binRecents=(true|false)""")

    /** Entries in the order listed: most recent first. */
    fun parse(dump: String): List<RecentTask> {
        val tasks = mutableListOf<RecentTask>()
        var id = -1
        var type = ""
        var comp: String? = null
        var flg = 0
        var recent = true
        var open = false
        fun close() {
            if (open) tasks += RecentTask(id, type, comp, flg, recent)
            open = false
        }
        for (line in dump.lineSequence()) {
            header.find(line)?.let { m ->
                close()
                id = m.groupValues[1].toInt()
                type = m.groupValues[2]
                comp = null
                flg = 0
                recent = true
                open = true
                return@let
            }
            if (!open) continue
            component.find(line)?.let { comp = expand(it.groupValues[1]) }
            if (line.trimStart().startsWith("intent=")) {
                flags.find(line)?.let { flg = it.groupValues[1].toLong(16).toInt() }
            }
            inRecents.find(line)?.let { recent = it.groupValues[1] == "true" }
        }
        close()
        return tasks
    }

    /** "pkg/.Cls" → "pkg/pkg.Cls", as the dump abbreviates components. */
    private fun expand(flat: String): String {
        val pkg = flat.substringBefore('/')
        val cls = flat.substringAfter('/', "")
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else flat
    }

    /** The tasks Clear all would remove, Pathfinder's own window included. */
    fun closable(tasks: List<RecentTask>, exclusions: Exclusions): List<RecentTask> =
        tasks.filter { task ->
            task.type == "standard" &&
                task.inRecents &&
                task.intentFlags and EXCLUDE_FROM_RECENTS == 0 &&
                task.packageName !in exclusions.packages &&
                task.component !in exclusions.activities
        }

    private val packageName = Regex("""^[A-Za-z0-9._]+$""")

    /**
     * The apps to force-stop once their tasks are gone: every closed app but
     * Pathfinder itself, whose process hosts the accessibility service.
     */
    fun stoppable(closing: List<RecentTask>, selfPackage: String): List<String> =
        closing.mapNotNull { it.packageName }.distinct().filter { it != selfPackage && packageName.matches(it) }

    sealed interface Outcome {
        data class Closed(val count: Int) : Outcome
        data object NothingToClose : Outcome
        data object NeedsShizuku : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** Closes every app in Recents. Blocking: run off the main thread. */
    fun closeAll(context: Context): Outcome {
        if (!Shell.ready) return Outcome.NeedsShizuku
        val dump = Shell.run("dumpsys", "activity", "recents")
        if (!dump.ok) return Outcome.Failed(dump.err.ifBlank { "dumpsys failed" })
        val tasks = closable(parse(dump.out), ScreenSwap.exclusions(context))
        val ids = tasks.map { it.id }
        val packages = stoppable(tasks, context.packageName)
        Log.i(TAG, "closing tasks $ids, stopping $packages")
        if (ids.isEmpty()) return Outcome.NothingToClose
        // The Thor's Clear all force-stops each app after removing its task, so
        // the process ends at once rather than lingering as a cached process.
        val stop = packages.joinToString("") { "; am force-stop $it" }
        // Then every screen goes home, as after Clear all: otherwise a screen
        // shows whatever was underneath. The injected Home has scan code 0, so
        // Pathfinder's own service ignores it.
        val home = context.getSystemService(DisplayManager::class.java).displays
            .joinToString("") { "; input -d ${it.displayId} keyevent KEYCODE_HOME" }
        // One task failing must not stop the rest, so no && between steps.
        val result = Shell.sh(
            "for t in \"\$@\"; do am stack remove \"\$t\"; done$stop$home",
            *ids.map(Int::toString).toTypedArray(),
        )
        if (!result.ok) return Outcome.Failed(result.err.lineSequence().firstOrNull { it.isNotBlank() } ?: "remove failed")
        return Outcome.Closed(ids.size)
    }
}

/** A message for the user, worded like Recents' own. */
fun closeAllOutcomeMessage(outcome: RecentTasks.Outcome): String = when (outcome) {
    is RecentTasks.Outcome.Closed -> "All tasks closed"
    RecentTasks.Outcome.NothingToClose -> "No tasks to close"
    RecentTasks.Outcome.NeedsShizuku -> "Closing tasks needs Shizuku"
    is RecentTasks.Outcome.Failed -> "Couldn't close tasks: ${outcome.message}"
}
