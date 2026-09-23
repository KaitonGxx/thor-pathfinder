package com.thorpathfinder.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import com.thorpathfinder.app.PathfinderService
import com.thorpathfinder.app.Profiles

/**
 * The question a "Profile switcher" shortcut set to Ask puts when it runs:
 * which profile? A see-through window of its own, started by the service,
 * which switches to whichever the user picks and goes away.
 */
class ProfileChoiceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profiles = Profiles.all(this)
        val active = Profiles.activeId(this)

        setContent {
            PathfinderTheme {
                val first = remember { FocusRequester() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            finish()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Taps on the card stay on it; only the dimmed area around dismisses.
                    Card(Modifier.widthIn(max = 420.dp).padding(16.dp).pointerInput(Unit) { detectTapGestures { } }) {
                        Column(Modifier.padding(vertical = 16.dp, horizontal = 12.dp)) {
                            Text(
                                "Switch to",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            profiles.forEachIndexed { index, profile ->
                                NavRow(
                                    profile.name,
                                    if (profile.id == active) "In use" else null,
                                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                                    onClick = {
                                        Profiles.switchTo(this@ProfileChoiceActivity, profile.id)
                                        PathfinderService.profileSwitched(this@ProfileChoiceActivity)
                                        finish()
                                    },
                                )
                            }
                        }
                    }
                }
                val inputMode = LocalInputModeManager.current.inputMode
                LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }
            }
        }
    }
}
