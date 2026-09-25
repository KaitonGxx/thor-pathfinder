package com.thorpathfinder.app

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Check For Updates: asks GitHub which release is the newest.
 *
 * This is Pathfinder's only network request, and it runs only when the button
 * is tapped. It is a plain GET with nothing about the user or the device in
 * it: the User-Agent names the app, where Android's default would name the
 * device model.
 */
object UpdateCheck {
    private const val REPO = "KaitonGxx/thor-pathfinder"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    /** GitHub redirects this to whichever release is newest. */
    const val LATEST_PAGE = "https://github.com/$REPO/releases/latest"

    /** Every APK Pathfinder will download starts with this. */
    const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"

    data class Release(val version: String, val page: String, val apk: String?)

    sealed interface Outcome {
        /** [latest] is newer than the installed version. */
        data class Available(val latest: Release) : Outcome
        data class UpToDate(val latest: Release) : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** Blocks for up to [TIMEOUT_MS] twice over: run it off the main thread. */
    fun check(words: Words, installed: String): Outcome {
        val connection = try {
            URL(API).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return Outcome.Failed(words.text(R.string.upd_offline))
        }
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Thor-Pathfinder")
        return try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val release = parse(connection.inputStream.bufferedReader().use { it.readText() })
                    when {
                        release == null -> Outcome.Failed(words.text(R.string.upd_bad_answer))
                        isNewer(release.version, installed) -> Outcome.Available(release)
                        else -> Outcome.UpToDate(release)
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Outcome.Failed(words.text(R.string.upd_no_releases))
                // Unsigned requests are limited to 60 an hour per network.
                HttpURLConnection.HTTP_FORBIDDEN, 429 ->
                    Outcome.Failed(words.text(R.string.upd_rate_limited))
                else -> Outcome.Failed(words.text(R.string.upd_error, code))
            }
        } catch (e: IOException) {
            Outcome.Failed(words.text(R.string.upd_offline))
        } finally {
            connection.disconnect()
        }
    }

    /** The release in GitHub's answer, or null if there isn't one. */
    fun parse(json: String): Release? = try {
        val release = JSONObject(json)
        val tag = release.optString("tag_name")
        if (tag.isBlank()) {
            null
        } else {
            Release(
                version = tag.trim().removePrefix("v").removePrefix("V"),
                // Only ever open a page on GitHub.
                page = release.optString("html_url").takeIf { it.startsWith("https://github.com/") }
                    ?: LATEST_PAGE,
                apk = apk(release),
            )
        }
    } catch (e: JSONException) {
        null
    }

    /** The release's own APK, or null when it has none to download. */
    private fun apk(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        return (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it)?.optString("browser_download_url") }
            .firstOrNull { it.startsWith(DOWNLOAD_PREFIX) && it.endsWith(".apk") }
    }

    /** Whether version [a] is newer than [b], number by number, so 0.10.0 is newer than 0.9.2. */
    fun isNewer(a: String, b: String): Boolean {
        val x = numbers(a)
        val y = numbers(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val order = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (order != 0) return order > 0
        }
        return false
    }

    /** "v1.2.3-beta" gives [1, 2, 3]. */
    private fun numbers(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    private const val TIMEOUT_MS = 10_000
}
