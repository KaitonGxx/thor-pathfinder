package com.thorpathfinder.app

import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The English strings, read straight from res/values/strings.xml, so tests of
 * the rules that build messages check the words people actually see.
 * Unit tests run in the module's directory, where the file is.
 */
object English : Words {

    private val strings = mutableMapOf<String, String>()
    private val plurals = mutableMapOf<String, Map<String, String>>()

    init {
        val file = File("src/main/res/values/strings.xml")
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            when (node.tagName) {
                "string" -> strings[name] = unescape(node.textContent)
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    plurals[name] = (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to unescape(item.textContent)
                    }
                }
            }
        }
    }

    private val stringNames by lazy { R.string::class.java.fields.associate { it.getInt(null) to it.name } }
    private val pluralNames by lazy { R.plurals::class.java.fields.associate { it.getInt(null) to it.name } }

    override fun text(id: Int, vararg args: Any): String {
        val name = stringNames.getValue(id)
        return String.format(Locale.US, strings.getValue(name), *args)
    }

    override fun count(id: Int, quantity: Int, vararg args: Any): String {
        val forms = plurals.getValue(pluralNames.getValue(id))
        val form = if (quantity == 1) forms["one"] ?: forms.getValue("other") else forms.getValue("other")
        return String.format(Locale.US, form, *args)
    }

    /** Android's escapes in a resource string; double quotes keep spaces at the ends. */
    private fun unescape(raw: String): String = raw
        .let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1) else it }
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\@", "@")
        .replace("\\?", "?")
}
