package com.thorpathfinder.app

import java.util.EnumMap

/** Where the engine reads the current shortcuts and timings from. */
interface GestureConfig {
    fun action(button: PhysicalButton, gesture: Gesture): ButtonAction
    val holdMs: Long
    val doubleMs: Long
}

/** Runs a task later; a main-thread Handler in the app, a fake clock in tests. */
fun interface Scheduler {
    fun schedule(delayMs: Long, task: () -> Unit): Cancellable
}

fun interface Cancellable {
    fun cancel()
}

/**
 * Turns raw key events into press / double-press / hold gestures.
 *
 * Everything runs on one thread (the accessibility service's main thread),
 * so the per-button state needs no locking.
 */
class GestureEngine(
    private val config: GestureConfig,
    private val scheduler: Scheduler,
    private val fire: (PhysicalButton, Gesture, ButtonAction) -> Unit,
) {
    private class State {
        /** A system button's down was swallowed, so its up must be too. */
        var intercepting = false
        var holdFired = false
        var holdTimer: Cancellable? = null
        /** A first press waiting to see whether a second one follows. */
        var pendingPress: Cancellable? = null
        var secondPress = false
        /** Gamepad buttons: when the last press ended, for double-press. */
        var lastUp: Long? = null
        /** The press in progress already fired a shortcut, so it can't start a pair. */
        var used = false
        /** When the swallowed press began, to tell a long press from a short one. */
        var downTime = 0L
        /** A press starting by this time is Pathfinder's own replay: let it through. */
        var passUntil = Long.MIN_VALUE
        /** The press in progress is being let through, down to its release. */
        var passing = false
    }

    private val states = EnumMap<PhysicalButton, State>(PhysicalButton::class.java)

    private fun state(button: PhysicalButton) = states.getOrPut(button) { State() }

    private fun mapped(button: PhysicalButton, gesture: Gesture) =
        config.action(button, gesture) != ButtonAction.NORMAL

    /**
     * Feeds one key event. Returns true when the event is consumed and must not
     * reach apps. A cancelled up (the system aborted the press) fires nothing.
     */
    fun onKey(button: PhysicalButton, down: Boolean, repeat: Int, time: Long, canceled: Boolean = false): Boolean =
        when (button.kind) {
            ButtonKind.SYSTEM -> onSystemKey(button, down, repeat, time, canceled)
            ButtonKind.GAMEPAD -> {
                onGamepadKey(button, down, repeat, time, canceled)
                false
            }
        }

    /**
     * Lets the next press of [button] that starts by [until] (event time) reach
     * the system untouched: Pathfinder is about to replay the real key.
     * [Long.MIN_VALUE] withdraws it.
     */
    fun letThrough(button: PhysicalButton, until: Long) {
        state(button).passUntil = until
    }

    /** Drops all timers, for when the service stops. */
    fun reset() {
        states.values.forEach {
            it.holdTimer?.cancel()
            it.pendingPress?.cancel()
        }
        states.clear()
    }

    private fun onSystemKey(button: PhysicalButton, down: Boolean, repeat: Int, time: Long, canceled: Boolean): Boolean {
        val s = state(button)
        if (s.passing) {
            if (!down) s.passing = false
            return false
        }
        if (down && repeat == 0 && time <= s.passUntil) {
            s.passUntil = Long.MIN_VALUE
            s.passing = true
            return false
        }
        if (down) {
            if (repeat > 0) return s.intercepting
            // Untouched buttons keep their own behaviour, with no delay at all.
            s.intercepting = button.gestures.any { mapped(button, it) }
            if (!s.intercepting) return false
            s.downTime = time
            s.holdFired = false
            s.secondPress = s.pendingPress != null
            s.pendingPress?.cancel()
            s.pendingPress = null
            s.holdTimer?.cancel()
            s.holdTimer = if (mapped(button, Gesture.HOLD)) {
                scheduler.schedule(config.holdMs) {
                    s.holdTimer = null
                    s.holdFired = true
                    fire(button, Gesture.HOLD, config.action(button, Gesture.HOLD))
                }
            } else null
            return true
        }

        if (!s.intercepting) return false
        s.intercepting = false
        s.holdTimer?.cancel()
        s.holdTimer = null
        when {
            canceled || s.holdFired -> Unit
            s.secondPress -> fire(button, Gesture.DOUBLE, config.action(button, Gesture.DOUBLE))
            // A long press with no hold shortcut is still a hold, left on Normal:
            // the AYN button, for one, does something else on a long press.
            time - s.downTime >= config.holdMs -> fire(button, Gesture.HOLD, config.action(button, Gesture.HOLD))
            // No double-press shortcut: act at once instead of waiting for a second press.
            !mapped(button, Gesture.DOUBLE) -> press(button)
            else -> s.pendingPress = scheduler.schedule(config.doubleMs) {
                s.pendingPress = null
                press(button)
            }
        }
        s.secondPress = false
        return true
    }

    private fun press(button: PhysicalButton) =
        fire(button, Gesture.PRESS, config.action(button, Gesture.PRESS))

    private fun onGamepadKey(button: PhysicalButton, down: Boolean, repeat: Int, time: Long, canceled: Boolean) {
        val s = state(button)
        if (!down) {
            s.holdTimer?.cancel()
            s.holdTimer = null
            s.lastUp = if (canceled || s.used) null else time
            s.used = false
            return
        }
        if (repeat > 0) return

        if (mapped(button, Gesture.DOUBLE)) {
            val lastUp = s.lastUp
            if (lastUp != null && time - lastUp <= config.doubleMs) {
                s.lastUp = null
                s.used = true // so a third quick press starts a new pair
                fire(button, Gesture.DOUBLE, config.action(button, Gesture.DOUBLE))
                return
            }
        }
        s.holdTimer?.cancel()
        s.holdTimer = if (mapped(button, Gesture.HOLD)) {
            scheduler.schedule(config.holdMs) {
                s.holdTimer = null
                s.used = true
                fire(button, Gesture.HOLD, config.action(button, Gesture.HOLD))
            }
        } else null
    }
}
