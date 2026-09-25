package com.thorpathfinder.app

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** What Pathfinder remembers about updates between runs. */
class UpdateSettings(context: Context) {

    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** Ask GitHub for the newest release when Pathfinder opens. */
    var checkOnOpen: Boolean
        get() = prefs.getBoolean("checkOnOpen", true)
        set(value) = prefs.edit { putBoolean("checkOnOpen", value) }

    /** Download and install an update by itself. Off until the user says so. */
    var autoInstall: Boolean
        get() = prefs.getBoolean("autoInstall", false)
        set(value) = prefs.edit { putBoolean("autoInstall", value) }

    var lastCheckMs: Long
        get() = prefs.getLong("lastCheckMs", 0L)
        set(value) = prefs.edit { putLong("lastCheckMs", value) }

    /** The release GitHub last reported, so the button knows before the check finishes. */
    var known: UpdateCheck.Release?
        get() {
            val version = prefs.getString("knownVersion", null) ?: return null
            val page = prefs.getString("knownPage", null) ?: return null
            return UpdateCheck.Release(version, page, prefs.getString("knownApk", null))
        }
        set(value) = prefs.edit {
            putString("knownVersion", value?.version)
            putString("knownPage", value?.page)
            putString("knownApk", value?.apk)
        }

    /** The version whose card the user asked not to see again. */
    var hiddenVersion: String?
        get() = prefs.getString("hiddenVersion", null)
        set(value) = prefs.edit { putString("hiddenVersion", value) }
}

/**
 * Fetching a release's APK and installing it.
 *
 * The install goes through Shizuku (`pm install-create` / `-write` / `-commit`,
 * with the APK on the command's stdin so no file has to be readable by the
 * shell user), which asks the user nothing. Without Shizuku there is no
 * silent route, so the release page is offered instead.
 *
 * Before anything is installed the APK is checked: it must come from
 * Pathfinder's own releases, carry Pathfinder's package name, be signed with
 * the same certificate as the copy already installed, and be newer than it.
 * Android would refuse a different signature anyway; this says so in words
 * the user can act on.
 */
object Updater {

    private const val TAG = "PathfinderUpdate"

    sealed interface Outcome {
        /** The install was handed over; this process is about to be replaced. */
        data object Installing : Outcome
        data class Failed(val message: String) : Outcome
    }

    fun canInstall(release: UpdateCheck.Release?): Boolean = release?.apk != null && Shell.ready

    /** Blocking: downloads, checks and installs. [onProgress] gets 0..100. */
    fun install(context: Context, release: UpdateCheck.Release, onProgress: (Int) -> Unit): Outcome {
        val url = release.apk ?: return Outcome.Failed(context.getString(R.string.inst_no_apk))
        if (!url.startsWith(UpdateCheck.DOWNLOAD_PREFIX)) {
            return Outcome.Failed(context.getString(R.string.inst_not_ours))
        }
        if (!Shell.ready) return Outcome.Failed(context.getString(R.string.inst_needs_shizuku))

        val file = File(context.cacheDir, "update.apk")
        try {
            download(context, url, file, onProgress)?.let { return Outcome.Failed(it) }
            check(context, file)?.let { return Outcome.Failed(it) }
            return handOver(context, file)
        } catch (e: IOException) {
            Log.w(TAG, "update failed", e)
            return Outcome.Failed(context.getString(R.string.inst_download_failed))
        } finally {
            file.delete()
        }
    }

    /** Null when the file arrived, a message when it didn't. */
    private fun download(context: Context, url: String, into: File, onProgress: (Int) -> Unit): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "Thor-Pathfinder")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return context.getString(R.string.inst_http, connection.responseCode)
            }
            val total = connection.contentLengthLong
            var read = 0L
            connection.inputStream.use { source ->
                into.outputStream().use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        sink.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress(((read * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            return if (into.length() == 0L) context.getString(R.string.inst_empty) else null
        } finally {
            connection.disconnect()
        }
    }

    /** Null when the APK is a genuine newer Pathfinder, a message when it isn't. */
    private fun check(context: Context, file: File): String? {
        val pm = context.packageManager
        val downloaded = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return context.getString(R.string.inst_broken)
        if (downloaded.packageName != context.packageName) return context.getString(R.string.inst_not_pathfinder)
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (fingerprints(downloaded.signingInfo) != fingerprints(installed.signingInfo)) {
            return context.getString(R.string.inst_other_key)
        }
        if (downloaded.longVersionCode <= installed.longVersionCode) {
            return context.getString(R.string.inst_not_newer)
        }
        return null
    }

    private fun fingerprints(info: android.content.pm.SigningInfo?): Set<String> {
        val signers = info?.apkContentsSigners ?: return emptySet()
        val sha = MessageDigest.getInstance("SHA-256")
        return signers.map { sha.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }

    /**
     * Streams the APK into a `pm` session as the shell user. The commit
     * replaces this very app, so it usually never returns: Android stops the
     * process as it installs.
     */
    private fun handOver(context: Context, file: File): Outcome {
        val size = file.length().toString()
        val created = Shell.run("pm", "install-create", "-r", "-S", size)
        val session = Regex("""\[(\d+)]""").find(created.out)?.groupValues?.get(1)
            ?: return Outcome.Failed(
                context.getString(R.string.inst_start_failed, created.err.trim().ifBlank { created.out.trim() }),
            )
        val written = Shell.pipe("pm", "install-write", "-S", size, session, "base", "-") { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        if (!written.ok) {
            Shell.run("pm", "install-abandon", session)
            return Outcome.Failed(context.getString(R.string.inst_write_failed, written.err.trim()))
        }
        val committed = Shell.run("pm", "install-commit", session)
        if (!committed.ok) {
            return Outcome.Failed(context.getString(R.string.inst_failed, committed.err.trim()))
        }
        return Outcome.Installing
    }

    private const val TIMEOUT_MS = 20_000
}
