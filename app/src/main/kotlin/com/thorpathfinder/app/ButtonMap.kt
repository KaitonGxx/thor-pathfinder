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
     * changed gesture with what it does, in gesture order; [combos] are the
     * lines for the combos this button is held for.
     */
    fun callout(
        words: Words,
        button: PhysicalButton,
        mapped: List<Pair<Gesture, String>>,
        combos: List<String> = emptyList(),
    ): Callout? =
        if (mapped.isEmpty() && combos.isEmpty()) {
            null
        } else {
            Callout(
                button,
                words.text(button.text),
                mapped.map { (gesture, what) -> words.text(R.string.map_line, words.text(gesture.text), what) } + combos,
            )
        }

    /**
     * A held button's combo lines: "+ A: Screenshot". Each combo is listed
     * once, on the first of its buttons to hold, and a box shows at most
     * [COMBO_LINES] of them so the picture keeps its shape.
     */
    fun comboLines(words: Words, button: PhysicalButton, combos: List<Pair<Set<ComboKey>, String>>): List<String> {
        val key = ComboKey.of(button)
        val mine = combos
            .filter { (keys, _) -> keys.filter { it in ComboKey.ANCHORS }.minByOrNull { it.ordinal } == key }
            .sortedWith(compareBy({ it.first.size }, { Combos.id(it.first) }))
            .map { (keys, what) -> words.text(R.string.map_combo_line, Combos.label(words, keys - key), what) }
        if (mine.size <= COMBO_LINES) return mine
        val more = mine.size - COMBO_LINES + 1
        return mine.take(COMBO_LINES - 1) + words.count(R.plurals.map_more_combos, more, more)
    }

    /** Every button that carries something, in the order they are listed. */
    fun callouts(context: Context, shortcuts: Shortcuts): List<Callout> {
        val words = context.words()
        val combos = shortcuts.combos.mapNotNull { keys ->
            shortcuts.combo(keys)?.let { keys to describe(context, it) }
        }
        return PhysicalButton.entries.mapNotNull { button ->
            callout(
                words,
                button,
                button.gestures.mapNotNull { gesture -> entry(context, shortcuts, button, gesture) },
                comboLines(words, button, combos),
            )
        }
    }

    private fun entry(
        context: Context,
        shortcuts: Shortcuts,
        button: PhysicalButton,
        gesture: Gesture,
    ): Pair<Gesture, String>? {
        val shortcut = shortcuts.shortcut(button, gesture)
        if (shortcut.action == ButtonAction.NORMAL) return null
        return gesture to describe(context, shortcut)
    }

    /**
     * What one shortcut does, in as few words as fit beside a picture. The
     * settings screen says the same thing at more length.
     */
    fun describe(context: Context, shortcut: Shortcut, words: Words = context.words()): String {
        return when (shortcut.action) {
            ButtonAction.HOME ->
                shortcut.home?.let { words.text(R.string.map_home_to, words.text(it.short)) }
                    ?: words.text(shortcut.action.text)
            ButtonAction.CLOSE_ALL -> words.text(shortcut.close.text)
            ButtonAction.LAUNCH_APP -> launchText(context, words, shortcut)
            ButtonAction.PROFILE -> when (shortcut.profile) {
                ProfileSwitch.CYCLE -> words.text(ProfileSwitch.CYCLE.text)
                ProfileSwitch.ASK -> words.text(R.string.map_profile_ask)
                ProfileSwitch.ENABLE -> words.text(R.string.map_profile_enable, Profiles.name(context, shortcut.profileId))
            }
            ButtonAction.FOCUS_MODE -> words.text(shortcut.focus.text)
            else -> words.text(shortcut.action.text)
        }
    }

    private fun launchText(context: Context, words: Words, shortcut: Shortcut): String {
        val app = appLabel(context, shortcut.app) ?: return words.text(ButtonAction.LAUNCH_APP.text)
        val second = appLabel(context, shortcut.second)
        return if (second != null) {
            words.text(R.string.map_open_pair, app, second)
        } else {
            words.text(R.string.map_open_one, app, words.text(shortcut.screen.short))
        }
    }

    /** Combo lines a box shows before it sums up the rest. */
    const val COMBO_LINES = 3

    private fun appLabel(context: Context, pkg: String?): String? = pkg?.let {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
        }.getOrDefault(it)
    }
}
