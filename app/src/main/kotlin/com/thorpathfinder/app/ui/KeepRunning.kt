package com.thorpathfinder.app.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.thorpathfinder.app.Shortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thor companions that do their work in the background: OdinTools,
 * ClusterTune and Pulse. Force-stopping one stops it watching for the app you
 * open next, so they are offered first. Each is declared in the manifest's
 * `queries`, or Android would hide it.
 */
private val RECOMMENDED = listOf("de.langerhans.odintools", "com.aure.clustertune", "com.kei.pulse")

private class KeepRunningApps(val recommended: List<AppEntry>, val rest: List<AppEntry>) {
    val size get() = recommended.size + rest.size
}

/**
 * The apps Close all apps leaves alone: their task still goes from the task
 * view, but the app itself is not force-stopped, so music, a download or
 * anything else it runs in the background carries on.
 */
@Composable
fun KeepRunningPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val shortcuts = remember { Shortcuts(context) }
    var kept by remember { mutableStateOf(shortcuts.keepRunning) }
    var apps by remember { mutableStateOf<KeepRunningApps?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { load(context) } }

    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(apps, inputMode) {
        if (apps != null) runCatching { first.requestFocus() }
    }

    fun toggle(pkg: String, want: Boolean) {
        kept = if (want) kept + pkg else kept - pkg
        shortcuts.keepRunning = kept
    }

    PageScaffold(
        "Keep apps running",
        "However Close app(s) closes them, all at once, one screen's, or picked by name, these " +
            "leave the task view like any other but are never force-stopped, so music, a download " +
            "or a sync keeps going.",
        onBack = onBack,
    ) {
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@PageScaffold
        }
        Text(
            if (kept.isEmpty()) "No apps chosen: everything is force-stopped."
            else "${kept.size} of ${list.size} apps kept running.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (list.recommended.isNotEmpty()) {
                item(key = "recommended") {
                    ListHeading(
                        "Highly recommended",
                        "These keep working in the background while you play, so they are best left running.",
                    )
                }
                items(list.recommended, key = { it.pkg }) { app ->
                    AppRow(
                        app = app,
                        checked = app.pkg in kept,
                        modifier = if (app === list.recommended.first()) Modifier.focusRequester(first) else Modifier,
                        onChange = { toggle(app.pkg, it) },
                    )
                }
                item(key = "all") { ListHeading("All apps") }
            }
            items(list.rest, key = { it.pkg }) { app ->
                AppRow(
                    app = app,
                    checked = app.pkg in kept,
                    modifier = if (list.recommended.isEmpty() && app === list.rest.firstOrNull()) {
                        Modifier.focusRequester(first)
                    } else {
                        Modifier
                    },
                    onChange = { toggle(app.pkg, it) },
                )
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .focusOutline()
            .clip(RowShape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

/** The launcher apps, with the recommended ones lifted out of the list. */
private fun load(context: Context): KeepRunningApps {
    val all = loadApps(context)
    val installed = RECOMMENDED.mapNotNull { pkg ->
        all.firstOrNull { it.pkg == pkg } ?: entry(context, pkg)
    }
    return KeepRunningApps(installed, all.filter { it.pkg !in RECOMMENDED })
}

/** A recommended app that has no launcher icon of its own. */
private fun entry(context: Context, pkg: String): AppEntry? = runCatching {
    val pm = context.packageManager
    val info = pm.getApplicationInfo(pkg, 0)
    AppEntry(pkg, pm.getApplicationLabel(info).toString(), info.loadIcon(pm).toBitmap(96, 96).asImageBitmap())
}.getOrNull()
