package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * global_mouse_mode_config.json is the AYN Thor's own file with AYN's
 * descriptive text (the "descriptions" and "titles" blocks) removed; the rest
 * is byte for byte, formatting included.
 */
class MouseModeTest {

    private val original = javaClass.classLoader!!.getResource("global_mouse_mode_config.json")!!.readText()

    @Test
    fun reversingChangesOnlyTheRightStickFlag() {
        val edited = MouseMode.withRightStickReverse(original, true)!!
        val changed = original.lines().zip(edited.lines()).filter { (a, b) -> a != b }
        assertEquals(listOf("      \"reverseJoystick\": false," to "      \"reverseJoystick\": true,"), changed)
        val lines = edited.lines()
        val at = lines.indexOf(changed.single().second)
        assertEquals("      \"reverseJoystick1\": false,", lines[at + 1])
        assertEquals(true, lines.drop(at).first { "\"name\"" in it }.contains("RIGHT_JOYSTICK"))
    }

    @Test
    fun turningItBackOffRestoresTheOriginalBytes() {
        val on = MouseMode.withRightStickReverse(original, true)!!
        assertEquals(original, MouseMode.withRightStickReverse(on, false))
        assertEquals(original, MouseMode.withRightStickReverse(original, false))
    }

    @Test
    fun worksOnCompactJson() {
        val compact = """{"joystickConfigs":[{"reverseJoystick":false,"name":"LEFT_JOYSTICK"},""" +
            """{"reverseJoystick":false,"reverseJoystick1":false,"name":"RIGHT_JOYSTICK"}]}"""
        assertEquals(
            """{"joystickConfigs":[{"reverseJoystick":false,"name":"LEFT_JOYSTICK"},""" +
                """{"reverseJoystick":true,"reverseJoystick1":false,"name":"RIGHT_JOYSTICK"}]}""",
            MouseMode.withRightStickReverse(compact, true),
        )
    }

    @Test
    fun missingEntryOrFieldGivesNull() {
        assertNull(MouseMode.withRightStickReverse("""{"joystickConfigs":[]}""", true))
        assertNull(MouseMode.withRightStickReverse("""[{"name":"RIGHT_JOYSTICK","type":3003}]""", true))
    }
}
