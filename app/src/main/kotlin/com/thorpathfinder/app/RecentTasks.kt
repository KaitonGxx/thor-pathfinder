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
 *
 * The apps on the user's keep-running list lose their task like the rest,
 * but are not force-stopped, so whatever they do in the background carries
 * on (see [Shortcuts.keepRunning]).
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
     * Pathfinder itself, whose process hosts the accessibility service, and
     * the ones the user asked to [keepRunning].
     */
    fun stoppable(
        closing: List<RecentTask>,
        selfPackage: String,
        keepRunning: Set<String> = emptySet(),
    ): List<String> = closing.mapNotNull { it.packageName }
        .distinct()
        .filter { shouldStop(it, selfPackage, keepRunning) }

    /** Whether a closed app is also force-stopped: not Pathfinder, and not on the keep-running list. */
    fun shouldStop(pkg: String, selfPackage: String, keepRunning: Set<String>): Boolean =
        pkg != selfPackage && pkg !in keepRunning && packageName.matches(pkg)

    /** An activity from `dumpsys activity activities`, with the task it sits in. */
    data class FocusedTask(val component: String, val id: Int) {
        val packageName: String get() = component.substringBefore('/')
    }

    // "  ResumedActivity: ActivityRecord{51112b4 u0 com.android.deskclock/.DeskClock} t166}"
    private val resumed = Regex("""^\s*ResumedActivity: ActivityRecord\{\S+ u\d+ ([^\s}]+)\}? t(\d+)""", RegexOption.MULTILINE)

    /**
     * The app the user is on: Android's one top resumed activity, which
     * follows whichever screen was touched last (checked on the Thor, where
     * the window manager's focused display agrees with it).
     */
    fun focused(dump: String): FocusedTask? =
        resumed.find(dump)?.let { FocusedTask(expand(it.groupValues[1]), it.groupValues[2].toInt()) }

    /** What closing one app, or a chosen few, came to. */
    sealed interface AppsOutcome {
        /** [what] names what went: an app, two apps, or a count. */
        data class Closed(val what: String) : AppsOutcome

        /** Nothing there to close; [where] when the question was about one screen. */
        data class NothingToClose(val where: String? = null) : AppsOutcome

        /** None of the chosen apps had a task open. */
        data object NoneRunning : AppsOutcome

        data object NeedsShizuku : AppsOutcome
        data class Failed(val message: String) : AppsOutcome
    }

    /** "Discord", "Discord and Firefox", or "3 apps". */
    fun names(labels: List<String>): String = when (labels.size) {
        0 -> "No apps"
        1 -> labels[0]
        2 -> "${labels[0]} and ${labels[1]}"
        else -> "${labels.size} apps"
    }

    /** The chosen packages that may be closed at all: never a home screen or System UI. */
    fun targets(packages: Set<String>, exclusions: Exclusions): List<String> =
        packages.filter { it !in exclusions.packages && it != SYSTEM_UI && packageName.matches(it) }.sorted()

    /**
     * Closes the focused app the way swiping it out of Recents does: its task
     * goes, and the app is force-stopped unless it's on the keep-running list.
     * Home screens are left alone. Blocking, like everything that closes.
     */
    fun closeFocused(context: Context, keepRunning: Set<String>): AppsOutcome {
        if (!Shell.ready) return AppsOutcome.NeedsShizuku
        val dump = Shell.run("dumpsys", "activity", "activities")
        if (!dump.ok) return AppsOutcome.Failed(dump.err.ifBlank { "dumpsys failed" })
        val task = focused(dump.out) ?: return AppsOutcome.NothingToClose()
        val pkg = task.packageName
        val exclusions = ScreenSwap.exclusions(context)
        if (pkg in exclusions.packages || task.component in exclusions.activities || pkg == SYSTEM_UI) {
            return AppsOutcome.NothingToClose()
        }
        return closeTask(context, task.id, pkg, keepRunning)
    }

    /**
     * Closes the app showing on [display], found the way a swap finds what to
     * move (the top visible task that isn't a home screen). [where] names the
     * screen in messages. A null [display] means there is no second screen.
     */
    fun closeOnScreen(context: Context, display: Int?, where: String, keepRunning: Set<String>): AppsOutcome {
        if (!Shell.ready) return AppsOutcome.NeedsShizuku
        if (display == null) return AppsOutcome.Failed("no second screen found")
        val list = Shell.run("am", "stack", "list")
        if (!list.ok) return AppsOutcome.Failed(list.err.ifBlank { "am stack list failed" })
        val app = ScreenSwap.visibleApp(ScreenSwap.parse(list.out), display, ScreenSwap.exclusions(context))
        val pkg = app?.topPackage
        if (app == null || pkg == null || pkg == SYSTEM_UI) return AppsOutcome.NothingToClose(where)
        return closeTask(context, app.id, pkg, keepRunning)
    }

    /**
     * Closes the chosen apps wherever they are: every task of theirs in
     * Recents goes, then each is force-stopped, even with no window open,
     * unless it's on the keep-running list.
     */
    fun closeApps(context: Context, packages: Set<String>, keepRunning: Set<String>): AppsOutcome {
        if (!Shell.ready) return AppsOutcome.NeedsShizuku
        val exclusions = ScreenSwap.exclusions(context)
        val chosen = targets(packages, exclusions)
        if (chosen.isEmpty()) return AppsOutcome.NothingToClose()
        val dump = Shell.run("dumpsys", "activity", "recents")
        if (!dump.ok) return AppsOutcome.Failed(dump.err.ifBlank { "dumpsys failed" })
        val tasks = closable(parse(dump.out), exclusions).filter { it.packageName in chosen }
        val open = running(tasks, chosen)
        // Package names are checked against [packageName] by targets(), so they're safe in the script.
        // Every chosen app is stopped, open or not, to catch one still working in the background.
        val stop = chosen.filter { shouldStop(it, context.packageName, keepRunning) }
        Log.i(TAG, "closing chosen apps $chosen: open $open, tasks ${tasks.map { it.id }}, stopping $stop")
        val script = "for t in \"\$@\"; do am stack remove \"\$t\"; done" + stop.joinToString("") { "; am force-stop $it" }
        val result = Shell.sh(script, *tasks.map { it.id.toString() }.toTypedArray())
        if (!result.ok) {
            return AppsOutcome.Failed(result.err.lineSequence().firstOrNull { it.isNotBlank() } ?: "close failed")
        }
        // The message counts only what was open, not everything on the list.
        if (open.isEmpty()) return AppsOutcome.NoneRunning
        return AppsOutcome.Closed(names(open.map { label(context, it) }))
    }

    /** The chosen apps that had a task open, in the order chosen. */
    fun running(tasks: List<RecentTask>, chosen: List<String>): List<String> =
        chosen.filter { pkg -> tasks.any { it.packageName == pkg } }

    /** Removes one task, then force-stops its app unless it's kept running or is Pathfinder. */
    private fun closeTask(context: Context, taskId: Int, pkg: String, keepRunning: Set<String>): AppsOutcome {
        val stop = shouldStop(pkg, context.packageName, keepRunning)
        Log.i(TAG, "closing task $taskId ($pkg), stop=$stop")
        val script = "am stack remove \"\$1\"" + if (stop) "; am force-stop \"\$2\"" else ""
        val result = Shell.sh(script, taskId.toString(), pkg)
        if (!result.ok) {
            return AppsOutcome.Failed(result.err.lineSequence().firstOrNull { it.isNotBlank() } ?: "remove failed")
        }
        return AppsOutcome.Closed(label(context, pkg))
    }

    private fun label(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private const val SYSTEM_UI = "com.android.systemui"

    sealed interface Outcome {
        data class Closed(val count: Int) : Outcome
        data object NothingToClose : Outcome
        data object NeedsShizuku : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** Closes every app in Recents. Blocking: run off the main thread. */
    fun closeAll(context: Context, keepRunning: Set<String> = emptySet()): Outcome {
        if (!Shell.ready) return Outcome.NeedsShizuku
        val dump = Shell.run("dumpsys", "activity", "recents")
        if (!dump.ok) return Outcome.Failed(dump.err.ifBlank { "dumpsys failed" })
        val tasks = closable(parse(dump.out), ScreenSwap.exclusions(context))
        val ids = tasks.map { it.id }
        val packages = stoppable(tasks, context.packageName, keepRunning)
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

/** A message for the user after closing one app, or a chosen few. */
fun closeAppsOutcomeMessage(outcome: RecentTasks.AppsOutcome): String = when (outcome) {
    is RecentTasks.AppsOutcome.Closed -> "${outcome.what} closed"
    is RecentTasks.AppsOutcome.NothingToClose -> outcome.where?.let { "No app on $it" } ?: "No app to close"
    RecentTasks.AppsOutcome.NoneRunning -> "No selected task(s) running"
    RecentTasks.AppsOutcome.NeedsShizuku -> "Closing apps needs Shizuku"
    is RecentTasks.AppsOutcome.Failed -> "Couldn't close it: ${outcome.message}"
}

/** A message for the user, worded like Recents' own. */
fun closeAllOutcomeMessage(outcome: RecentTasks.Outcome): String = when (outcome) {
    is RecentTasks.Outcome.Closed -> "All tasks closed"
    RecentTasks.Outcome.NothingToClose -> "No tasks to close"
    RecentTasks.Outcome.NeedsShizuku -> "Closing tasks needs Shizuku"
    is RecentTasks.Outcome.Failed -> "Couldn't close tasks: ${outcome.message}"
}
