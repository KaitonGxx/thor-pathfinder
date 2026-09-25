package com.thorpathfinder.app

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * Pathfinder's own language, apart from the Thor's: Android 13's per-app
 * language (LocaleManager), the same setting as Settings → Apps → Thor
 * Pathfinder → Language. None chosen means the Thor's. Android restarts the
 * app's screens with the new language by itself; the service's messages
 * follow too, since they are looked up each time.
 */
object Language {

    /** The languages Pathfinder ships, as tags, each named in its own language. */
    val CHOICES: List<Pair<String, String>> = listOf(
        "en" to "English",
        "es" to "Español",
        "pt-BR" to "Português (Brasil)",
        "zh-CN" to "简体中文",
        "ja" to "日本語",
    )

    /** The chosen tag, or null when Pathfinder follows the Thor. */
    fun chosen(context: Context): String? {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
        if (locales.isEmpty) return null
        val tag = locales[0].toLanguageTag()
        // Android may hand back more detail than was set (a script, say): match on what we offer.
        return CHOICES.map { it.first }.firstOrNull { it.equals(tag, ignoreCase = true) }
            ?: CHOICES.map { it.first }.firstOrNull { tag.startsWith(it.substringBefore('-'), ignoreCase = true) }
    }

    /** Chooses [tag], or the Thor's language when null. */
    fun choose(context: Context, tag: String?) {
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    /** The name of the chosen language in its own words, or null when following the Thor. */
    fun chosenName(context: Context): String? = chosen(context)?.let { tag -> CHOICES.firstOrNull { it.first == tag }?.second }
}
