package com.thorpathfinder.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Switching Pathfinder's own accessibility service on again, through Shizuku.
 *
 * Only the shell user may write `enabled_accessibility_services`, which is
 * why an app cannot normally grant itself accessibility, and why Shizuku can.
 * This is deliberately something the user presses rather than something
 * Pathfinder does by itself: a service that silently switches itself back on
 * would also come back when someone turned it off on purpose.
 *
 * It is the same off-and-on that Android's own list does, so it fixes both
 * the switch being off and the switch being on with nothing started. First it
 * takes Pathfinder off AYN's auto launch list ([AutoLaunchList]), which
 * otherwise keeps the service from starting however the switch is set.
 *
 * Blocking: run it off the main thread.
 */
object ServiceSwitch {

    private const val SETTING = "enabled_accessibility_services"

    sealed interface Outcome {
        data object Done : Outcome
        data object NeedsShizuku : Outcome
        data class Failed(val message: String) : Outcome
    }

    fun turnOn(context: Context): Outcome {
        if (!Shell.ready) return Outcome.NeedsShizuku
        // Before the off-and-on, or Android refuses the service all over again.
        AutoLaunchList.removeUs(context)?.let { return Outcome.Failed(it) }
        val ours = ComponentName(context, PathfinderService::class.java).flattenToString()

        // Read what is there now, never a remembered copy: writing a stale
        // list would switch somebody else's service off.
        val current = read(context)
        val others = current.filter { !it.startsWith(context.packageName + "/") }
        val listed = current.size != others.size

        // Already listed means Android has it on and never started it, so the
        // list has to change for Android to look again: off, then on.
        if (listed) {
            val off = write(others)
            if (off != null) return Outcome.Failed(off)
        }
        val on = write(others + ours)
        if (on != null) {
            // Put back what was there rather than leave it switched off.
            if (listed) write(current)
            return Outcome.Failed(on)
        }
        return Outcome.Done
    }

    private fun read(context: Context): List<String> =
        Settings.Secure.getString(context.contentResolver, SETTING)
            .orEmpty()
            .split(':')
            .filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != null }

    /** Null when the write went through, a message when it didn't. */
    private fun write(components: List<String>): String? {
        val value = components.joinToString(":")
        // An empty list still has to be written, so pass it as an argument.
        val result = Shell.run("settings", "put", "secure", SETTING, value)
        if (result.ok) return null
        return result.err.lineSequence().firstOrNull { it.isNotBlank() } ?: "couldn't write the setting"
    }
}

/** What to tell the user after pressing "Turn it back on". */
fun serviceSwitchMessage(outcome: ServiceSwitch.Outcome): String = when (outcome) {
    ServiceSwitch.Outcome.Done -> "Switched back on"
    ServiceSwitch.Outcome.NeedsShizuku -> "Switching it on from here needs Shizuku"
    is ServiceSwitch.Outcome.Failed -> "Couldn't switch it on: ${outcome.message}"
}
