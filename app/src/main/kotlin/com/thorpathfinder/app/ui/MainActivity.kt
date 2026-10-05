package com.thorpathfinder.app.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.database.ContentObserver
import android.graphics.drawable.Icon
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
import com.thorpathfinder.app.PathfinderService
import com.thorpathfinder.app.PhysicalButton
import com.thorpathfinder.app.ProfileTileService
import com.thorpathfinder.app.R
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
                    // Once, for someone updating: what this version brings, before the main screen.
                    var welcome by rememberSaveable {
                        mutableStateOf(setupAt == null && Welcome.due(this@MainActivity, shortcuts.setupDone))
                    }
                    var settingsAt by rememberSaveable { mutableStateOf<ManagePage?>(null) }
                    var languageAt by rememberSaveable { mutableStateOf(false) }
                    var levelsAt by rememberSaveable { mutableStateOf(false) }
                    var openCard by rememberSaveable { mutableStateOf<PhysicalButton?>(null) }
                    Box(Modifier.safeDrawingPadding()) {
                        val step = setupAt
                        if (step != null) {
                            SetupWizard(
                                state = current,
                                startAt = step,
                                onRequestShizuku = ::requestShizuku,
                                onFinish = {
                                    shortcuts.setupDone = true
                                    // Setup has shown them around; what's new is all new to them.
                                    Welcome.markSeen(this@MainActivity)
                                    setupAt = null
                                },
                            )
                        } else if (welcome) {
                            WelcomeScreen(
                                onOpen = { link ->
                                    Welcome.markSeen(this@MainActivity)
                                    welcome = false
                                    when (link) {
                                        WelcomeLink.LEVELS -> openCard = PhysicalButton.BACK
                                        WelcomeLink.DISABLED -> {
                                            settingsAt = ManagePage.PROFILES
                                            settings = true
                                        }
                                        WelcomeLink.TILE -> askToAddTile()
                                        WelcomeLink.VOLUME_SWAP -> {
                                            levelsAt = true
                                            settings = true
                                        }
                                    }
                                },
                                onDone = {
                                    Welcome.markSeen(this@MainActivity)
                                    welcome = false
                                },
                            )
                        } else if (settings) {
                            MoreSettingsScreen(
                                current,
                                openAt = settingsAt,
                                openLanguage = languageAt,
                                openLevels = levelsAt,
                                onBack = {
                                    settings = false
                                    settingsAt = null
                                    languageAt = false
                                    levelsAt = false
                                },
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
                                openCard = openCard,
                                onWhatsNew = { welcome = true },
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Android 13's own "add this tile?" question, for the profile tile, so it
     * goes into Quick Settings without a trip through the tile editor.
     */
    private fun askToAddTile() {
        val asked = runCatching {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, ProfileTileService::class.java),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_tile_profile),
                mainExecutor,
            ) { result ->
                // Android skips its question when the tile is already there, so Quick Settings
                // opens either way to show it; also when Android couldn't ask, so it can be added
                // from the editor there. Only a "Don't add" leaves things be.
                if (result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED) {
                    PathfinderService.openQuickSettings()
                }
            }
        }.isSuccess
        if (!asked) PathfinderService.openQuickSettings()
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
