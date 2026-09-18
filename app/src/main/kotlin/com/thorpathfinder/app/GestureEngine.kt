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
            ButtonKind.SYSTEM -> onSystemKey(button, down, repeat, canceled)
            ButtonKind.GAMEPAD -> {
                onGamepadKey(button, down, repeat, time, canceled)
                false
            }
        }

    /** Drops all timers, for when the service stops. */
    fun reset() {
        states.values.forEach {
            it.holdTimer?.cancel()
            it.pendingPress?.cancel()
        }
        states.clear()
    }

    private fun onSystemKey(button: PhysicalButton, down: Boolean, repeat: Int, canceled: Boolean): Boolean {
        val s = state(button)
        if (down) {
            if (repeat > 0) return s.intercepting
            // Untouched buttons keep their own behaviour, with no delay at all.
            s.intercepting = button.gestures.any { mapped(button, it) }
            if (!s.intercepting) return false
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
