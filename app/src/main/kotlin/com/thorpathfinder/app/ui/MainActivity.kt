package com.thorpathfinder.app.ui

import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thorpathfinder.app.ServiceLog
import com.thorpathfinder.app.Shortcuts
import com.thorpathfinder.app.SystemState
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var state by mutableStateOf<SystemState?>(null)
    private var preview: Preview? = null
    private var watcher: ContentObserver? = null

    // Shizuku calls these on its own threads.
    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshOnUiThread() }
    private val binderDead = Shizuku.OnBinderDeadListener { refreshOnUiThread() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refreshOnUiThread() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        val shortcuts = Shortcuts(this)
        preview = Preview.from(this)
        refresh()

        setContent {
            PathfinderTheme {
                Surface(Modifier.fillMaxSize()) {
                    val current = state ?: return@Surface
                    var setupAt by rememberSaveable {
                        mutableStateOf(preview?.step ?: if (shortcuts.setupDone) null else SetupStep.WELCOME)
                    }
                    var settings by rememberSaveable { mutableStateOf(false) }
                    Box(Modifier.safeDrawingPadding()) {
                        val step = setupAt
                        if (step != null) {
                            SetupWizard(
                                state = current,
                                startAt = step,
                                onRequestShizuku = ::requestShizuku,
                                onFinish = {
                                    shortcuts.setupDone = true
                                    setupAt = null
                                },
                            )
                        } else if (settings) {
                            MoreSettingsScreen(
                                current,
                                onBack = { settings = false },
                                onRunSetup = {
                                    settings = false
                                    setupAt = SetupStep.WELCOME
                                },
                            )
                        } else {
                            SettingsScreen(
                                current,
                                onFix = { setupAt = it },
                                onOpenSettings = { settings = true },
                            )
                        }
                    }
                }
            }
        }
    }

    // Coming back from Android's settings or from Shizuku is when things change.
    override fun onResume() {
        super.onResume()
        // Watched here as well as in the service: when the switch goes off the
        // service stops, and only the app is left to notice it come back.
        if (watcher == null) {
            val handler = Handler(Looper.getMainLooper())
            watcher = ServiceLog.watch(this, handler) {
                refresh()
                // Android binds a moment after the setting changes, so look again.
                handler.postDelayed({ refresh() }, REBIND_MS)
            }
        }
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        ServiceLog.stop(this, watcher)
        watcher = null
        super.onDestroy()
    }

    private fun refresh() {
        val real = SystemState.read(this)
        state = preview?.apply(real) ?: real
    }

    private fun refreshOnUiThread() = runOnUiThread { refresh() }

    private fun requestShizuku() {
        runCatching { Shizuku.requestPermission(REQUEST_SHIZUKU) }
    }

    private companion object {
        const val REQUEST_SHIZUKU = 1

        /** Long enough for Android to have started the service after the switch moved. */
        const val REBIND_MS = 1500L
    }
}
