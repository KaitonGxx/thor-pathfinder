package com.thorpathfinder.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.thorpathfinder.app.ButtonAction
import com.thorpathfinder.app.PathfinderService
import com.thorpathfinder.app.Shell

/**
 * The Shortcut menu: every shortcut in one list, the same one a gesture is
 * set from in the app, with the same questions after the ones with an arrow.
 * Picking one runs it once instead of setting anything. A see-through window
 * of its own on the top screen, started by the service like the other
 * questions; the service runs the pick once this window has gone.
 */
class ShortcutMenuActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PathfinderTheme {
                ShortcutPicker(
                    title = ButtonAction.SHORTCUT_MENU.label,
                    choices = ButtonAction.menuChoices,
                    current = null,
                    shizukuReady = Shell.ready,
                    onChosen = { shortcut ->
                        finish()
                        PathfinderService.runFromMenu(shortcut)
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }
}
