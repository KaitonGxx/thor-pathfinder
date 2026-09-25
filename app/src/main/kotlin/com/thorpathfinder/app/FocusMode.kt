package com.thorpathfinder.app

import android.content.Context
import android.provider.Settings

/**
 * The Thor's Focus Mode: which screen the controller drives.
 *
 * AYN's Focus Mode menu (DualScreenAssistant) offers Auto-lock, Top screen
 * and Bottom screen, and all it does is write `Settings.System` [KEY] as 0, 1
 * or 2. OdinSettings, a persistent system app, watches that setting and does
 * the rest: it sets `persist.sys.input.dispaly` to the same number, which the
 * input stack reads, and for the two locks taps (-10, -10) on that screen to
 * move focus there. So writing the setting through Shizuku is exactly what
 * choosing in AYN's menu does.
 *
 * Blocking: run it off the main thread.
 */
object FocusMode {

    const val KEY = "screen_focus_lock"

    const val AUTO = 0
    const val TOP = 1
    const val BOTTOM = 2

    /** AYN's own names, in its menu's order; each one's position is the value stored. */
    val NAMES = listOf(R.string.focus_mode_auto, R.string.focus_mode_top, R.string.focus_mode_bottom)

    sealed interface Outcome {
        data class Changed(val value: Int) : Outcome
        data object NeedsShizuku : Outcome
        data class Failed(val message: String) : Outcome
    }

    /**
     * The one after [current], round in the menu's order. Anything unknown
     * counts as Auto-lock, the Thor's own default.
     */
    fun next(current: Int): Int {
        val from = if (current in NAMES.indices) current else AUTO
        return (from + 1) % NAMES.size
    }

    /** The mode [switch] moves to from [current]. */
    fun target(switch: FocusSwitch, current: Int): Int = when (switch) {
        FocusSwitch.TOP -> TOP
        FocusSwitch.BOTTOM -> BOTTOM
        FocusSwitch.CYCLE -> next(current)
        // Only between the two screens; from Auto-lock, or anything unknown, the top comes first.
        FocusSwitch.SWAP -> if (current == TOP) BOTTOM else TOP
    }

    fun name(words: Words, value: Int): String? = NAMES.getOrNull(value)?.let { words.text(it) }

    fun current(context: Context): Int = Settings.System.getInt(context.contentResolver, KEY, AUTO)

    /** Moves Focus Mode the way [switch] says. */
    fun apply(context: Context, switch: FocusSwitch): Outcome {
        if (!Shell.ready) return Outcome.NeedsShizuku
        val current = current(context)
        val target = target(switch, current)
        // Already there: nothing to write, but the message still says where focus is.
        if (target == current) return Outcome.Changed(target)
        val result = Shell.run("settings", "put", "system", KEY, target.toString())
        if (!result.ok) {
            return Outcome.Failed(result.err.lineSequence().firstOrNull { it.isNotBlank() } ?: "couldn't change it")
        }
        return Outcome.Changed(target)
    }
}

/** What the shortcut says when it runs. */
fun focusModeMessage(words: Words, outcome: FocusMode.Outcome): String = when (outcome) {
    is FocusMode.Outcome.Changed -> words.text(R.string.msg_focus_mode, words.text(FocusMode.NAMES[outcome.value]))
    FocusMode.Outcome.NeedsShizuku -> words.text(R.string.msg_focus_needs_shizuku)
    is FocusMode.Outcome.Failed -> words.text(R.string.msg_focus_failed, outcome.message)
}
