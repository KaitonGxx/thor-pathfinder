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

        data class Supported(val firmware: String) : Support
        data class NotAThor(val model: String) : Support
        data class OldFirmware(val firmware: String) : Support
        data object UnknownFirmware : Support
    }

    val current: Support by lazy { check(Build.MANUFACTURER, Build.MODEL, Build.DISPLAY, Build.TIME) }

    fun check(manufacturer: String, model: String, display: String, buildTime: Long): Support {
        if (!manufacturer.equals("AYN", ignoreCase = true) || !model.equals("AYN Thor", ignoreCase = true)) {
            return Support.NotAThor(model)
        }
        val version = firmware(display)
            ?: return if (buildTime >= MIN_BUILD_TIME) Support.Supported(display) else Support.UnknownFirmware
        val text = version.joinToString(".")
        return if (compare(version, MIN_FIRMWARE) >= 0) Support.Supported(text) else Support.OldFirmware(text)
    }

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
