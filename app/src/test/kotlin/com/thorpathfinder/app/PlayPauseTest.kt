package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayPauseTest {

    @Test
    fun everyButtonCanCarryIt() {
        for (button in PhysicalButton.entries) {
            assertTrue(ButtonAction.PLAY_PAUSE in ButtonAction.choicesFor(button))
        }
    }

    @Test
    fun theMenuAndCombosOfferIt() {
        assertTrue(ButtonAction.PLAY_PAUSE in ButtonAction.menuChoices)
        assertTrue(ButtonAction.PLAY_PAUSE in ButtonAction.comboChoices)
    }

    @Test
    fun itNeedsNoShizuku() {
        // Android's media key goes through AudioManager, which any app may use.
        assertFalse(ButtonAction.PLAY_PAUSE.needsShizuku)
    }

    @Test
    fun theListPutsItAfterVolume() {
        val list = ButtonAction.entries
        assertEquals(ButtonAction.VOLUME, list[list.indexOf(ButtonAction.PLAY_PAUSE) - 1])
    }

    @Test
    fun itIsNamedInTheListTheCardsAndThePicture() {
        // The cards and the Thor picture fall back to the action's own name.
        assertEquals("Play/pause media", English.text(ButtonAction.PLAY_PAUSE.text))
    }

    @Test
    fun itSendsTheKeyThatToggles() {
        // KEYCODE_MEDIA_PLAY_PAUSE, not PLAY or PAUSE: nothing has to know whether media is playing.
        assertEquals(85, MediaKey.CODE)
    }
}
