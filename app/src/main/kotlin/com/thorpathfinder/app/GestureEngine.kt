package com.thorpathfinder.app

import java.util.EnumMap

/** Where the engine reads the current shortcuts and timings from. */
interface GestureConfig {
    fun action(button: PhysicalButton, gesture: Gesture): ButtonAction
    val holdMs: Long
    val doubleMs: Long

    /** Every combo that does something, each as its set of buttons (see [Combos]). */
    val combos: Set<Set<ComboKey>> get() = emptySet()
}

/** Runs a task later; a main-thread Handler in the app, a fake clock in tests. */
fun interface Scheduler {
    fun schedule(delayMs: Long, task: () -> Unit): Cancellable
}

fun interface Cancellable {
    fun cancel()
}

/**
 * Turns raw key events into press / double-press / hold gestures, and into
 * combos.
 *
 * A combo starts from Back, Home or the AYN button held down (and taken
 * over), before its Hold has run. Any button pressed with it that makes a
 * combo, or the start of one, is kept from apps with its repeats and release,
 * and the held button's own gestures are off for that press. A combo that is
 * also the start of a three-button one waits [CHORD_MS] for the third, or
 * runs as soon as one of its buttons is let go. The held button stays held,
 * so another combo can follow.
 *
 * Everything runs on one thread (the accessibility service's main thread),
 * so the per-button state needs no locking.
 */
class GestureEngine(
    private val config: GestureConfig,
    private val scheduler: Scheduler,
    /**
     * Whether a plain press of the button can be handed back to the system
     * afterwards, by replaying the real key or by a stand-in action.
     */
    private val canRestore: (PhysicalButton) -> Boolean = { true },
    private val fireCombo: (Set<ComboKey>) -> Unit = {},
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
        /** The press in progress went into a combo, so its release does nothing more. */
        var combined = false
    }

    /** A combo being pressed: the button held for it, and every button in it so far. */
    private class Chord(val anchor: PhysicalButton) {
        val keys = mutableSetOf(ComboKey.of(anchor))
        /** Set while a combo that could still grow waits for its third button. */
        var waiting: Cancellable? = null
    }

    private val states = EnumMap<PhysicalButton, State>(PhysicalButton::class.java)
    private var chord: Chord? = null

    /** Buttons whose press went into a combo: their repeats and release are kept from apps too. */
    private val swallowed = mutableSetOf<ComboKey>()

    private fun state(button: PhysicalButton) = states.getOrPut(button) { State() }

    private fun mapped(button: PhysicalButton, gesture: Gesture) =
        config.action(button, gesture) != ButtonAction.NORMAL

    /**
     * Feeds one key event: [key] is the button as a combo sees it, [button]
     * the one with gestures of its own; either may be null. Returns true when
     * the event is consumed and must not reach apps.
     */
    fun onKey(
        key: ComboKey?,
        button: PhysicalButton?,
        down: Boolean,
        repeat: Int,
        time: Long,
        canceled: Boolean = false,
    ): Boolean {
        if (key != null && key in swallowed) {
            if (!down) {
                swallowed -= key
                released(key)
            }
            return true
        }
        if (key != null && down && repeat == 0 && join(key)) return true
        return button != null && onKey(button, down, repeat, time, canceled)
    }

    /**
     * Feeds one key event of a button with gestures. Returns true when the
     * event is consumed and must not reach apps. A cancelled up (the system
     * aborted the press) fires nothing.
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
        chord?.waiting?.cancel()
        chord = null
        swallowed.clear()
    }

    /**
     * Takes [key] into a combo when a button to hold for one is down and some
     * combo has both: true when it did, and the key is then kept from apps.
     */
    private fun join(key: ComboKey): Boolean {
        // The buttons held for a combo: the one already in use first, then the earliest pressed.
        // Checked before the combos themselves, since every button in a game comes through here.
        val held = states.entries
            .filter { (button, s) -> button.kind == ButtonKind.SYSTEM && s.intercepting && !s.holdFired }
            .sortedWith(compareBy({ chord?.anchor != it.key }, { it.value.downTime }))
            .map { it.key }
        if (held.isEmpty()) return false
        val combos = config.combos
        if (combos.isEmpty()) return false
        for (anchor in held) {
            if (ComboKey.of(anchor) == key) continue
            val before = chord?.takeIf { it.anchor == anchor }?.keys ?: setOf(ComboKey.of(anchor))
            val keys = before + key
            if (combos.none { it.containsAll(keys) }) continue
            val c = chord?.takeIf { it.anchor == anchor } ?: Chord(anchor).also {
                chord?.waiting?.cancel()
                chord = it
            }
            val s = state(anchor)
            s.combined = true
            s.holdTimer?.cancel()
            s.holdTimer = null
            c.keys += key
            swallowed += key
            c.waiting?.cancel()
            c.waiting = null
            if (keys in combos) {
                if (combos.any { it.size > keys.size && it.containsAll(keys) }) {
                    c.waiting = scheduler.schedule(CHORD_MS) { complete(c) }
                } else {
                    complete(c)
                }
            }
            return true
        }
        return false
    }

    /** Runs the combo pressed so far. The held button stays in, so another combo can follow. */
    private fun complete(c: Chord) {
        c.waiting?.cancel()
        c.waiting = null
        val keys = c.keys.toSet()
        c.keys.retainAll(setOf(ComboKey.of(c.anchor)))
        if (Combos.valid(keys)) fireCombo(keys)
    }

    /**
     * A button in the combo was let go: a combo waiting for a third button
     * runs now, and a start that never became a combo loses that button.
     */
    private fun released(key: ComboKey) {
        val c = chord ?: return
        if (key !in c.keys) return
        if (c.waiting != null) complete(c) else c.keys -= key
    }

    /** Whether [button] is in any combo, which is reason enough to take it over. */
    private fun inCombo(button: PhysicalButton): Boolean {
        val key = ComboKey.of(button)
        return config.combos.any { key in it }
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
            // So do buttons whose plain press Pathfinder could not give back: the
            // AYN button has no stand-in action, so without Shizuku swallowing it
            // would cost the user the AYN menu itself. A press that is a shortcut
            // never needs giving back, so that button is still swallowed.
            s.intercepting = (button.gestures.any { mapped(button, it) } || inCombo(button)) &&
                (mapped(button, Gesture.PRESS) || canRestore(button))
            if (!s.intercepting) return false
            s.downTime = time
            s.holdFired = false
            s.combined = false
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
        val combined = s.combined
        s.combined = false
        // Letting go of the held button ends its combo; one waiting for a third runs now.
        chord?.takeIf { it.anchor == button }?.let { c ->
            if (c.waiting != null) complete(c)
            chord = null
        }
        when {
            canceled || s.holdFired || combined -> Unit
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

    private companion object {
        /** How long a combo that could still grow waits for its third button. */
        const val CHORD_MS = 150L
    }
}
