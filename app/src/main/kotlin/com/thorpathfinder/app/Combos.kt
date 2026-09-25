package com.thorpathfinder.app

import android.view.KeyEvent
import androidx.annotation.StringRes

/**
 * The buttons a combo can be made of: every button the Thor's controller
 * reports as a key (see the Odin Controller's key layout), plus the AYN
 * button. Volume is left out, since it keeps its own job.
 */
enum class ComboKey(@StringRes val text: Int, val keyCode: Int) {
    BACK(R.string.button_back, KeyEvent.KEYCODE_BACK),
    HOME(R.string.button_home, KeyEvent.KEYCODE_HOME),
    AYN(R.string.key_ayn, KeyEvent.KEYCODE_HOME),
    A(R.string.key_a, KeyEvent.KEYCODE_BUTTON_A),
    B(R.string.key_b, KeyEvent.KEYCODE_BUTTON_B),
    X(R.string.key_x, KeyEvent.KEYCODE_BUTTON_X),
    Y(R.string.key_y, KeyEvent.KEYCODE_BUTTON_Y),
    UP(R.string.key_up, KeyEvent.KEYCODE_DPAD_UP),
    DOWN(R.string.key_down, KeyEvent.KEYCODE_DPAD_DOWN),
    LEFT(R.string.key_left, KeyEvent.KEYCODE_DPAD_LEFT),
    RIGHT(R.string.key_right, KeyEvent.KEYCODE_DPAD_RIGHT),
    L1(R.string.key_l1, KeyEvent.KEYCODE_BUTTON_L1),
    R1(R.string.key_r1, KeyEvent.KEYCODE_BUTTON_R1),
    L2(R.string.key_l2, KeyEvent.KEYCODE_BUTTON_L2),
    R2(R.string.key_r2, KeyEvent.KEYCODE_BUTTON_R2),
    L3(R.string.key_l3, KeyEvent.KEYCODE_BUTTON_THUMBL),
    R3(R.string.key_r3, KeyEvent.KEYCODE_BUTTON_THUMBR),
    SELECT(R.string.button_select, KeyEvent.KEYCODE_BUTTON_SELECT),
    START(R.string.button_start, KeyEvent.KEYCODE_BUTTON_START);

    /** The button with shortcuts of its own that this key is, if any. */
    val button: PhysicalButton?
        get() = when (this) {
            BACK -> PhysicalButton.BACK
            HOME -> PhysicalButton.HOME
            AYN -> PhysicalButton.AYN
            SELECT -> PhysicalButton.SELECT
            START -> PhysicalButton.START
            L3 -> PhysicalButton.L3
            R3 -> PhysicalButton.R3
            else -> null
        }

    companion object {
        /**
         * The buttons a combo starts from, by being held: the ones Pathfinder
         * can keep from apps, so neither they nor what is pressed with them
         * reach the game.
         */
        val ANCHORS = setOf(BACK, HOME, AYN)

        fun of(button: PhysicalButton): ComboKey = entries.first { it.button == button }

        /** AYN's USB vendor id, which the Thor's own controller reports in every style. */
        const val AYN_VENDOR = 0x2020

        /**
         * The Thor's controller in AYN's Xbox style. Standard style is product
         * 0x0111; Xbox style is made anew as "Xbox Wireless Controller",
         * product 0x0112, and sends the scan codes of A and B, and of X and Y,
         * the other way round, over an identical key layout. So Android (and
         * every game) sees the printed A as B, and so on.
         */
        const val XBOX_STYLE_PRODUCT = 0x0112

        /**
         * The key a real press is, or null, by the letter printed on the
         * button: in Xbox style ([xboxStyle]) A and B, and X and Y, are
         * swapped back, so a combo means the same button in every style.
         * Back, Home and the AYN button are told apart by scan code, as
         * [PhysicalButton.of] does; the rest need one too, since keys Android
         * injects itself carry scan code 0.
         */
        fun of(keyCode: Int, scanCode: Int, xboxStyle: Boolean = false): ComboKey? {
            PhysicalButton.of(keyCode, scanCode)?.let { return of(it) }
            if (scanCode == 0) return null
            val key = entries.firstOrNull { it !in ANCHORS && it.keyCode == keyCode } ?: return null
            return if (xboxStyle) printed(key) else key
        }

        /** The printed letter of a face button that Xbox style reports as [key]. */
        fun printed(key: ComboKey): ComboKey = when (key) {
            A -> B
            B -> A
            X -> Y
            Y -> X
            else -> key
        }
    }
}

/**
 * Button combos: two or three buttons pressed together, starting with Back,
 * Home or the AYN button held down. A combo is the set of its buttons, so
 * Back + Home + A is one combo however it was pressed, and it is kept under
 * [id] in the profile's shortcuts.
 */
object Combos {

    const val MAX = 3

    /** Whether [keys] can be a combo: two or three buttons, at least one of them one to hold. */
    fun valid(keys: Set<ComboKey>): Boolean = keys.size in 2..MAX && keys.any { it in ComboKey.ANCHORS }

    /** The name a combo is stored under: its buttons in a fixed order. */
    fun id(keys: Set<ComboKey>): String = keys.sortedBy { it.ordinal }.joinToString("+") { it.name }

    fun parse(id: String): Set<ComboKey>? {
        val keys = id.split('+').map { name -> ComboKey.entries.firstOrNull { it.name == name } ?: return null }
        return keys.toSet().takeIf { it.size == keys.size && valid(it) }
    }

    /** How a combo reads: the buttons held first, then the rest, joined by +. */
    fun label(words: Words, keys: Set<ComboKey>): String =
        keys.sortedBy { it.ordinal }.map { words.text(it.text) }.reduce { a, b -> words.text(R.string.combo_join, a, b) }

    /** The combos that [key] is in, for its button's card. */
    fun including(combos: Set<Set<ComboKey>>, key: ComboKey): List<Set<ComboKey>> =
        combos.filter { key in it }.sortedWith(compareBy({ it.size }, { id(it) }))
}
