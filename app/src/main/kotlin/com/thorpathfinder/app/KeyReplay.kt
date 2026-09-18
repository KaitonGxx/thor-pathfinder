package com.thorpathfinder.app

import java.util.Locale

/**
 * Replaying a physical button press through the kernel.
 *
 * The AYN button's own functions can't be triggered any other way. AYN's key
 * handler (PhoneWindowManager, scan code 194) consumes the key and sends the
 * broadcast `action.tcc.button.key.event`, which is protected: only the system
 * may send it. But the shell user is in the `input` group, so through Shizuku
 * `sendevent` can write the press to the button's own input device, and
 * Android handles it exactly like a real press. Blocking.
 */
object KeyReplay {

    private const val DEVICES = "/proc/bus/input/devices"
    private const val EV_KEY = 1
    private const val EV_SYN = 0

    /** "/dev/input/eventN" of the device called [name], from /proc/bus/input/devices. */
    fun devicePath(devices: String, name: String): String? {
        var matching = false
        for (line in devices.lineSequence()) {
            when {
                line.startsWith("N: Name=") -> matching = line.removePrefix("N: Name=").trim().trim('"') == name
                matching && line.startsWith("H: Handlers=") ->
                    return line.removePrefix("H: Handlers=").split(' ')
                        .firstOrNull { it.matches(Regex("""event\d+""")) }
                        ?.let { "/dev/input/$it" }
                line.isBlank() -> matching = false
            }
        }
        return null
    }

    /**
     * Presses [scanCode] on the device called [deviceName] for [holdMs]. The
     * release is always sent, even if the press failed half way, so the key
     * can never be left down.
     */
    fun press(deviceName: String, scanCode: Int, holdMs: Long): Boolean {
        if (!Shell.ready) return false
        val devices = Shell.run("cat", DEVICES)
        val path = if (devices.ok) devicePath(devices.out, deviceName) else null
        if (path == null) return false
        val hold = String.format(Locale.US, "%.2f", holdMs / 1000.0)
        val script = "sendevent \"\$1\" $EV_KEY $scanCode 1; sendevent \"\$1\" $EV_SYN 0 0; sleep $hold; " +
            "sendevent \"\$1\" $EV_KEY $scanCode 0; sendevent \"\$1\" $EV_SYN 0 0"
        return Shell.sh(script, path).ok
    }
}
