package com.thorpathfinder.app

import android.content.Context
import androidx.core.content.edit

/**
 * A watcher that outlives Pathfinder.
 *
 * Everything else Pathfinder does to keep its accessibility service on needs
 * Pathfinder to be running, which is no use against whatever is stopping it.
 * This starts a small script through Shizuku instead: it runs as the shell
 * user, belongs to Shizuku rather than to this app, and carries on when this
 * app is force-stopped or killed.
 *
 * Off unless the user turns it on. It only ever adds Pathfinder's own
 * component to the accessibility list, never removes anyone else's, and it
 * leaves the switch alone while Android's settings are open so that turning
 * the service off on purpose still works. It also takes Pathfinder, and only
 * Pathfinder, off AYN's auto launch list ([AutoLaunchList]), which otherwise
 * keeps the service from starting after a restart. The script is
 * `watchdog.sh` in the assets, and it is the whole of what runs.
 *
 * Blocking: run it off the main thread.
 */
object Watchdog {

    /** How often to look, in seconds. Five is quick without being wasteful. */
    val CHOICES = listOf(5, 10, 30, 60)
    const val DEFAULT_SECONDS = 5

    private const val PREFS = "watchdog"
    private const val EVERY = "everySeconds"
    private const val DIR = "/data/local/tmp"
    private const val SCRIPT = "$DIR/thorpathfinder-watchdog.sh"
    private const val MARKER = "$DIR/thorpathfinder-watchdog.on"
    private const val LOG = "$DIR/thorpathfinder-watchdog.log"

    /** What the running script's command line contains, for pgrep and pkill. */
    private const val PATTERN = "thorpathfinder-watchdog.sh"

    sealed interface Outcome {
        data object Started : Outcome
        data object Stopped : Outcome
        data object NeedsShizuku : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** How often the watchdog looks, in seconds. */
    fun seconds(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(EVERY, DEFAULT_SECONDS)

    /** Changes the interval, restarting the watchdog when it is running. */
    fun setSeconds(context: Context, value: Int): Outcome {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) { putInt(EVERY, value) }
        return if (on(context)) start(context) else Outcome.Stopped
    }

    /** Whether the watchdog is meant to be running; cheap enough to ask often. */
    fun on(context: Context): Boolean {
        if (!Shell.ready) return false
        return Shell.run("sh", "-c", "[ -f $MARKER ] && echo yes").out.trim() == "yes"
    }

    /** Whether a copy really is running, which is not the same as being meant to. */
    fun alive(): Boolean {
        if (!Shell.ready) return false
        // Run directly, not through sh -c: a wrapping shell carries the pattern
        // in its own command line, so pgrep would find the shell asking and
        // always answer yes.
        return Shell.run("pgrep", "-f", PATTERN).out.isNotBlank()
    }

    fun start(context: Context): Outcome {
        if (!Shell.ready) return Outcome.NeedsShizuku
        val script = runCatching {
            context.assets.open("watchdog.sh").bufferedReader().use { it.readText() }
        }.getOrElse { return Outcome.Failed("couldn't read the watchdog script") }

        // Write it where the shell user can reach it. The app's own files are
        // not readable from there, so it has to be copied out.
        val written = Shell.run("sh", "-c", "cat > $SCRIPT", input = script)
        if (!written.ok) return Outcome.Failed(written.err.trim().ifBlank { "couldn't write the script" })

        stopProcesses()
        val marked = Shell.run("sh", "-c", "touch $MARKER")
        if (!marked.ok) return Outcome.Failed(marked.err.trim().ifBlank { "couldn't turn it on" })

        // setsid so it is not a child of this command, and its output goes
        // nowhere so reading the command's own output can't wait on it.
        val started = Shell.run("sh", "-c", "setsid sh $SCRIPT ${seconds(context)} >/dev/null 2>&1 &")
        if (!started.ok) return Outcome.Failed(started.err.trim().ifBlank { "couldn't start it" })
        return Outcome.Started
    }

    /**
     * Starts it again if it was left switched on but isn't running, which is
     * how every restart leaves it: it runs under Shizuku, and Shizuku starts
     * afresh. The service calls this whenever Shizuku becomes available,
     * whether that is at boot or later by hand. Switched off, the marker is
     * gone and this does nothing, so it never overrides the user's choice.
     * True when it started one.
     */
    fun resume(context: Context): Boolean {
        if (!Shell.ready || !on(context) || alive()) return false
        return start(context) == Outcome.Started
    }

    fun stop(): Outcome {
        if (!Shell.ready) return Outcome.NeedsShizuku
        // The marker going is what tells the loop to finish; the kill is for
        // the copy that is asleep in the middle of a wait.
        Shell.run("sh", "-c", "rm -f $MARKER")
        stopProcesses()
        return Outcome.Stopped
    }

    /** What it has done, newest last; empty when it has done nothing. */
    fun log(): List<String> {
        if (!Shell.ready) return emptyList()
        val result = Shell.run("sh", "-c", "cat $LOG 2>/dev/null")
        if (!result.ok) return emptyList()
        return result.out.lines().filter { it.isNotBlank() }.takeLast(20)
    }

    private fun stopProcesses() {
        // Directly for the same reason: through sh -c, pkill kills its own shell.
        Shell.run("pkill", "-f", PATTERN)
    }
}

/** What to tell the user after turning the watchdog on or off. */
fun watchdogMessage(outcome: Watchdog.Outcome): String = when (outcome) {
    Watchdog.Outcome.Started -> "Watchdog on"
    Watchdog.Outcome.Stopped -> "Watchdog off"
    Watchdog.Outcome.NeedsShizuku -> "The watchdog needs Shizuku"
    is Watchdog.Outcome.Failed -> "Couldn't change the watchdog: ${outcome.message}"
}
