package com.thorpathfinder.app

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Play/pause media: one press of Android's play/pause media key, the one a
 * Bluetooth headset's play/pause button sends.
 *
 * `AudioManager.dispatchMediaKeyEvent` needs no permission. Android's media
 * session service hands the key to the app playing now, else to the one that
 * played last, so it reaches a video or music app on either screen, or one
 * behind the game, without switching to it. Nothing comes back to
 * say whether anything heard it.
 *
 * Apps built on Android's own media session act on the down after the
 * double-tap gap (300 ms). Two presses inside it skip to the next track, as
 * with a headset's button, where the app offers one; where it doesn't, they
 * cancel out and nothing happens.
 */
object MediaKey {

    const val CODE = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE

    /** Presses and releases the key. */
    fun playPause(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val down = SystemClock.uptimeMillis()
        // The up must carry the down's downTime: on a non-TV device MediaSessionService
        // holds a PLAY_PAUSE down (a long press would start voice search) until the
        // matching up, then delivers both.
        audio.dispatchMediaKeyEvent(KeyEvent(down, down, KeyEvent.ACTION_DOWN, CODE, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, CODE, 0))
    }
}
