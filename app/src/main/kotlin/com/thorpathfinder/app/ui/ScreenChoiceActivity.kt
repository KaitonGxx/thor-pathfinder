package com.thorpathfinder.app.ui

import android.os.Bundle
import android.widget.Toast
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
import com.thorpathfinder.app.LaunchScreen
import com.thorpathfinder.app.Launcher
import kotlin.concurrent.thread

/**
 * The question an "Ask" shortcut puts when it runs: open the app on the top
 * screen or the bottom one? A see-through window of its own, started by the
 * service, which opens the app where the user says and goes away.
 */
class ScreenChoiceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (pkg == null) {
            finish()
            return
        }
        val name = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("the app")

        setContent {
            PathfinderTheme {
                val top = remember { FocusRequester() }
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
                                "Open $name on",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            NavRow(
                                "Top screen",
                                "The main screen",
                                modifier = Modifier.focusRequester(top),
                                onClick = { openOn(pkg, LaunchScreen.TOP) },
                            )
                            NavRow("Bottom screen", "The second screen") { openOn(pkg, LaunchScreen.BOTTOM) }
                        }
                    }
                }
                val inputMode = LocalInputModeManager.current.inputMode
                LaunchedEffect(inputMode) { runCatching { top.requestFocus() } }
            }
        }
    }

    // Stays up until the launch is done, so a problem can still be shown here.
    private fun openOn(pkg: String, screen: LaunchScreen) {
        thread {
            val problem = Launcher.open(this, pkg, screen)
            runOnUiThread {
                if (problem != null) Toast.makeText(this, problem, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
    }
}
