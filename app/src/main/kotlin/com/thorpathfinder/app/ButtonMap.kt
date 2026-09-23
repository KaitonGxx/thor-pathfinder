package com.thorpathfinder.app

import android.content.Context

/**
 * What the Thor's buttons do in one profile, ready to be drawn.
 *
 * Positions are a fraction of the picture's width and height, so the drawing
 * can be any size. They come from the Thor itself, not from AYN's key test
 * screen, which lays the buttons out in a row of its own making.
 */
object ButtonMap {

    /** Which way a button's box hangs off the picture. */
    enum class Side { LEFT, RIGHT }

    /** Where a button is on the Thor, and which side its box belongs on. */
    data class Spot(val x: Float, val y: Float, val side: Side)

    /** One button's box: what it is, and what it does in this profile. */
    data class Callout(val button: PhysicalButton, val title: String, val lines: List<String>)

    /**
     * Where each button is on an open Thor, as fractions of the whole
     * picture: the lid above, the control deck below. The deck is staggered,
     * left stick above the D-pad and face buttons above the right stick, with
     * Select and Start at the bottom screen's top corners, Home and Back at
     * its bottom corners, and the AYN button on the bottom edge.
     */
    val SPOTS: Map<PhysicalButton, Spot> = mapOf(
        PhysicalButton.SELECT to Spot(0.190f, 0.574f, Side.LEFT),
        PhysicalButton.START to Spot(0.798f, 0.574f, Side.RIGHT),
        PhysicalButton.L3 to Spot(0.088f, 0.660f, Side.LEFT),
        PhysicalButton.R3 to Spot(0.874f, 0.819f, Side.RIGHT),
        PhysicalButton.HOME to Spot(0.179f, 0.936f, Side.LEFT),
        PhysicalButton.BACK to Spot(0.814f, 0.936f, Side.RIGHT),
        // Dead centre, so either side would do; on the left its line does not
        // have to cross Back's on the way to the middle of the bottom edge.
        PhysicalButton.AYN to Spot(0.505f, 0.974f, Side.LEFT),
    )

    /**
     * One button's box, or null when nothing is on it. [mapped] is each
     * changed gesture with what it does, in gesture order.
     */
    fun callout(button: PhysicalButton, mapped: List<Pair<Gesture, String>>): Callout? =
        if (mapped.isEmpty()) {
            null
        } else {
            Callout(button, button.label, mapped.map { (gesture, what) -> "${gesture.label}: $what" })
        }

    /** Every button that carries something, in the order they are listed. */
    fun callouts(context: Context, shortcuts: Shortcuts): List<Callout> =
        PhysicalButton.entries.mapNotNull { button ->
            callout(button, button.gestures.mapNotNull { gesture -> entry(context, shortcuts, button, gesture) })
        }

    private fun entry(
        context: Context,
        shortcuts: Shortcuts,
        button: PhysicalButton,
        gesture: Gesture,
    ): Pair<Gesture, String>? {
        val action = shortcuts.action(button, gesture)
        if (action == ButtonAction.NORMAL) return null
        return gesture to describe(context, shortcuts, button, gesture, action)
    }

    /**
     * What one gesture does, in as few words as fit beside a picture. The
     * settings screen says the same thing at more length.
     */
    fun describe(
        context: Context,
        shortcuts: Shortcuts,
        button: PhysicalButton,
        gesture: Gesture,
        action: ButtonAction,
    ): String = when (action) {
        ButtonAction.HOME -> shortcuts.home(button, gesture)?.let { "Home (${it.short})" } ?: action.label
        ButtonAction.CLOSE_ALL -> shortcuts.close(button, gesture).label
        ButtonAction.LAUNCH_APP -> launchText(context, shortcuts, button, gesture)
        ButtonAction.PROFILE -> when (shortcuts.profile(button, gesture)) {
            ProfileSwitch.CYCLE -> ProfileSwitch.CYCLE.label
            ProfileSwitch.ASK -> "Switch profile (ask)"
            ProfileSwitch.ENABLE -> "Enable " + Profiles.name(context, shortcuts.profileId(button, gesture))
        }
        else -> action.label
    }

    private fun launchText(
        context: Context,
        shortcuts: Shortcuts,
        button: PhysicalButton,
        gesture: Gesture,
    ): String {
        val app = appLabel(context, shortcuts.app(button, gesture)) ?: return ButtonAction.LAUNCH_APP.label
        val second = appLabel(context, shortcuts.second(button, gesture))
        return if (second != null) "Open $app + $second" else "Open $app (${shortcuts.screen(button, gesture).short})"
    }

    private fun appLabel(context: Context, pkg: String?): String? = pkg?.let {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
        }.getOrDefault(it)
    }
}
