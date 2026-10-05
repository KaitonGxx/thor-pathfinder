package com.thorpathfinder.app

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What a Brightness or Volume shortcut's slider changes. */
enum class LevelKind { BRIGHTNESS, VOLUME }

/**
 * The screens a Brightness or Volume slider changes. It opens on the top
 * screen; Up and Down move along this order, Both in the middle.
 */
enum class LevelScreens(@StringRes val text: Int) {
    TOP(R.string.launch_top),
    BOTH(R.string.home_both),
    BOTTOM(R.string.launch_bottom);

    val up: LevelScreens get() = entries[(ordinal - 1).coerceAtLeast(0)]
    val down: LevelScreens get() = entries[(ordinal + 1).coerceAtMost(entries.size - 1)]

    val top: Boolean get() = this != BOTTOM
    val bottom: Boolean get() = this != TOP
}

/**
 * The arithmetic of the Brightness and Volume sliders, and their settings.
 *
 * Brightness is a percentage on Android's own slider curve (BrightnessUtils'
 * HLG curve), so equal steps look equal from dark to bright; Android itself
 * takes a linear 0..1. Volume is the Thor's 0..15 on both screens: the media
 * volume on the top screen, AYN's `secondary_screen_volume_level` on the
 * bottom one.
 */
object Levels {

    const val VOLUME_MAX = 15
    const val BRIGHTNESS_MAX = 100

    /** A press never takes brightness below this: at 0 a screen can look switched off. */
    const val BRIGHTNESS_MIN = 1

    fun max(kind: LevelKind) = if (kind == LevelKind.BRIGHTNESS) BRIGHTNESS_MAX else VOLUME_MAX

    fun min(kind: LevelKind) = if (kind == LevelKind.BRIGHTNESS) BRIGHTNESS_MIN else 0

    /** Both screens' levels, on the slider's scale. */
    data class Pair(val top: Int, val bottom: Int)

    /** [levels] moved by [delta] on the screens [screens] covers, each kept within min..max. */
    fun nudge(levels: Pair, screens: LevelScreens, delta: Int, min: Int, max: Int): Pair = Pair(
        if (screens.top) (levels.top + delta).coerceIn(min, max) else levels.top,
        if (screens.bottom) (levels.bottom + delta).coerceIn(min, max) else levels.bottom,
    )

    /**
     * [levels] after the slider was dragged to [value]. On one screen that is
     * its level; on both, the top screen's, with the bottom one moved by the
     * same amount so the gap between them stays.
     */
    fun drag(levels: Pair, screens: LevelScreens, value: Int, min: Int, max: Int): Pair {
        val shown = if (screens == LevelScreens.BOTTOM) levels.bottom else levels.top
        return nudge(levels, screens, value - shown, min, max)
    }

    // Android's BrightnessUtils: the slider's HLG curve, with 0..1 standing for its 0..65535.
    private const val R_ = 0.5
    private const val A_ = 0.17883277
    private const val B_ = 0.28466892
    private const val C_ = 0.55991073

    /** A brightness as Android takes it (0..1, linear) for a slider [percent]. */
    fun percentToBrightness(percent: Int): Float {
        val gamma = percent.coerceIn(0, 100) / 100.0
        val linear = if (gamma <= R_) (gamma / R_) * (gamma / R_) else exp((gamma - C_) / A_) + B_
        return (linear.coerceIn(0.0, 12.0) / 12.0).toFloat()
    }

    /** Where Android's brightness (0..1, linear) sits on the slider, as a percentage. */
    fun brightnessToPercent(brightness: Float): Int {
        val linear = brightness.coerceIn(0f, 1f) * 12.0
        val gamma = if (linear <= 1.0) sqrt(linear) * R_ else A_ * ln(linear - B_) + C_
        return (gamma * 100).roundToInt().coerceIn(0, 100)
    }

    /** Android's stored brightness (1..255) as 0..1, as Android converts it. */
    fun storedToBrightness(stored: Int): Float = ((stored - 1) / 254f).coerceIn(0f, 1f)

    // Settings: device-wide, beside the other screen preferences.
    private const val PREFS = "ui"
    private const val BRIGHTNESS_STEP = "levels.brightnessStep"
    private const val VOLUME_STEP = "levels.volumeStep"
    private const val HOLD_SPEED = "levels.holdSpeed"
    private const val PLACE = "levels.place"
    private const val BOTTOM_BRIGHTNESS = "levels.bottomBrightness"

    val BRIGHTNESS_STEPS = listOf(1, 2, 5, 10, 20)
    val VOLUME_STEPS = listOf(1, 2, 3)
    const val DEFAULT_BRIGHTNESS_STEP = 5
    const val DEFAULT_VOLUME_STEP = 1

    /** How fast a held Left or Right keeps going. */
    enum class HoldSpeed(@StringRes val text: Int, val intervalMs: Long) {
        SLOW(R.string.levels_speed_slow, 200L),
        NORMAL(R.string.levels_speed_normal, 110L),
        FAST(R.string.levels_speed_fast, 55L),
    }

    /** Where on the top screen the slider appears. */
    enum class Place(@StringRes val text: Int) {
        TOP(R.string.levels_place_top),
        BOTTOM(R.string.levels_place_bottom),
    }

    fun place(context: Context): Place =
        prefs(context).getString(PLACE, null)?.let { name -> Place.entries.firstOrNull { it.name == name } }
            ?: Place.TOP

    fun setPlace(context: Context, place: Place) {
        prefs(context).edit { putString(PLACE, place.name) }
    }

    fun steps(kind: LevelKind) = if (kind == LevelKind.BRIGHTNESS) BRIGHTNESS_STEPS else VOLUME_STEPS

    /** How much one press, or one tick of a hold, changes [kind]. */
    fun step(context: Context, kind: LevelKind): Int = prefs(context).getInt(
        if (kind == LevelKind.BRIGHTNESS) BRIGHTNESS_STEP else VOLUME_STEP,
        if (kind == LevelKind.BRIGHTNESS) DEFAULT_BRIGHTNESS_STEP else DEFAULT_VOLUME_STEP,
    ).takeIf { it in steps(kind) } ?: if (kind == LevelKind.BRIGHTNESS) DEFAULT_BRIGHTNESS_STEP else DEFAULT_VOLUME_STEP

    fun setStep(context: Context, kind: LevelKind, step: Int) {
        prefs(context).edit { putInt(if (kind == LevelKind.BRIGHTNESS) BRIGHTNESS_STEP else VOLUME_STEP, step) }
    }

    fun holdSpeed(context: Context): HoldSpeed =
        prefs(context).getString(HOLD_SPEED, null)?.let { name -> HoldSpeed.entries.firstOrNull { it.name == name } }
            ?: HoldSpeed.NORMAL

    fun setHoldSpeed(context: Context, speed: HoldSpeed) {
        prefs(context).edit { putString(HOLD_SPEED, speed.name) }
    }

    /**
     * The bottom screen's brightness as last seen, for the slider to start
     * on before the helper (the only thing that can read it) is connected.
     */
    fun lastBottomBrightness(context: Context): Int? =
        prefs(context).getInt(BOTTOM_BRIGHTNESS, -1).takeIf { it >= 0 }

    fun setLastBottomBrightness(context: Context, percent: Int) {
        prefs(context).edit { putInt(BOTTOM_BRIGHTNESS, percent) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
