package com.thorpathfinder.app

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

/**
 * Shell commands through Shizuku, which runs them as the shell user (UID
 * 2000), the same user as `adb shell`. All calls block: use a background thread.
 */
object Shell {

    private const val TAG = "PathfinderShell"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    enum class Status { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    fun status(context: Context): Status = when {
        !isRunning() -> if (isInstalled(context)) Status.NOT_RUNNING else Status.NOT_INSTALLED
        !hasPermission() -> Status.NO_PERMISSION
        else -> Status.READY
    }

    val ready: Boolean get() = isRunning() && hasPermission()

    private fun isRunning() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private fun hasPermission() =
        runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    private fun isInstalled(context: Context) =
        runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess

    data class Result(val exit: Int, val out: String, val err: String) {
        val ok get() = exit == 0
    }

    /** Runs [command] with optional [input] on its stdin. */
    fun run(vararg command: String, input: String? = null): Result {
        val process = newProcess(arrayOf(*command))
        process.outputStream.use { if (input != null) it.write(input.toByteArray()) }
        var err = ""
        val errReader = thread { err = process.errorStream.bufferedReader().readText() }
        val out = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        errReader.join()
        if (exit != 0) Log.w(TAG, "${command.joinToString(" ")} → $exit ${err.trim()}")
        return Result(exit, out, err)
    }

    /** Runs a `sh -c` [script]; [args] arrive as $1, $2… so they need no quoting. */
    fun sh(script: String, vararg args: String, input: String? = null): Result =
        run("sh", "-c", script, "sh", *args, input = input)

    // Shizuku 13.1.5 made newProcess private in favour of its UserService.
    // Short, occasional commands don't justify a bound service, so reach the
    // method directly (kept by proguard-rules.pro).
    private fun newProcess(command: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, command, null, null) as Process
    }
}
