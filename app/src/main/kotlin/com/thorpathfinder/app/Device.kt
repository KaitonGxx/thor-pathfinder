package com.thorpathfinder.app

import android.os.Build

/**
 * Which devices and firmware Pathfinder runs on.
 *
 * Pathfinder leans on details of AYN's software: the mouse-mode setting and
 * config file, the button scan codes, the second screen. They were verified
 * on Thor firmware 1.0.0.377 (Build.DISPLAY "Thor_V1.0.0.377_20260206_165408_user",
 * built 2026-02-06), so that is the minimum. Newer firmware is accepted.
 */
object Device {

    val MIN_FIRMWARE = listOf(1, 0, 0, 377)
    const val MIN_FIRMWARE_TEXT = "1.0.0.377"

    /** That firmware's build time, for firmware whose name doesn't parse. */
    const val MIN_BUILD_TIME = 1_770_366_536_000L

    sealed interface Support {
        val ok: Boolean get() = this is Supported

        data class Supported(val firmware: String, val model: String = THOR) : Support
        data class NotAThor(val model: String) : Support
        data class OldFirmware(val firmware: String) : Support
        data object UnknownFirmware : Support
    }

    val current: Support by lazy { check(Build.MANUFACTURER, Build.MODEL, Build.DISPLAY, Build.TIME) }

    fun check(manufacturer: String, model: String, display: String, buildTime: Long): Support {
        if (!manufacturer.equals("AYN", ignoreCase = true) || !isThor(model)) return Support.NotAThor(model)
        // The firmware minimum is the Thor's own numbering. Other members of the
        // family (the Thor Lite) have their own, so they aren't held to it.
        if (!model.equals(THOR, ignoreCase = true)) {
            return Support.Supported(firmware(display)?.joinToString(".") ?: display, model)
        }
        val version = firmware(display)
            ?: return if (buildTime >= MIN_BUILD_TIME) Support.Supported(display) else Support.UnknownFirmware
        val text = version.joinToString(".")
        return if (compare(version, MIN_FIRMWARE) >= 0) Support.Supported(text) else Support.OldFirmware(text)
    }

    const val THOR = "AYN Thor"
    const val THOR_LITE = "AYN Thor Lite"

    /** Models Pathfinder has been run on: the Thor, and the Thor Lite (reported working by an owner). */
    fun tested(model: String) = model.equals(THOR, ignoreCase = true) || model.equals(THOR_LITE, ignoreCase = true)

    /** "AYN Thor" itself, and variants such as the "AYN Thor Lite". */
    private fun isThor(model: String) =
        model.equals(THOR, ignoreCase = true) || model.startsWith("$THOR ", ignoreCase = true)

    /** "Thor_V1.0.0.377_20260206_165408_user" → [1, 0, 0, 377]. */
    fun firmware(display: String): List<Int>? =
        Regex("""_V(\d+(?:\.\d+)+)""").find(display)?.groupValues?.get(1)?.split('.')?.map { it.toInt() }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}
