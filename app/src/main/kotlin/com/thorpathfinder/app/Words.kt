package com.thorpathfinder.app

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Words shown to people, looked up by resource id. In the app they come from
 * Android's resources in whichever language is in use; the rules that build
 * messages take a [Words] so tests can hand them the English file instead.
 */
interface Words {
    fun text(@StringRes id: Int, vararg args: Any): String

    fun count(@PluralsRes id: Int, quantity: Int, vararg args: Any): String
}

private class ContextWords(private val context: Context) : Words {
    override fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    override fun count(id: Int, quantity: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, quantity, *args)
}

/** This context's words, in the language it is using. */
fun Context.words(): Words = ContextWords(this)

/**
 * English, whatever language Pathfinder is showing: the diagnostics report
 * stays in English, since it is read by whoever fixes the bug.
 */
fun Context.englishWords(): Words {
    val english = Configuration(resources.configuration).apply { setLocale(Locale.ENGLISH) }
    return createConfigurationContext(english).words()
}
