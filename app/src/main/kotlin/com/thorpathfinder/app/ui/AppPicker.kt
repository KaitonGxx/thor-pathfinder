package com.thorpathfinder.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap)

/** Pick an app for a shortcut to open. */
@Composable
fun AppPickerDialog(
    title: String = "Choose an app",
    exclude: String? = null,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context).filter { it.pkg != exclude } }
    }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(apps, inputMode) {
        if (!apps.isNullOrEmpty()) runCatching { first.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).padding(horizontal = 12.dp)) {
                        items(list, key = { it.pkg }) { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .then(if (app === list.first()) Modifier.focusRequester(first) else Modifier)
                                    .focusOutline()
                                    .clip(RowShape)
                                    .clickable { onPick(app.pkg) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp).focusOutline(PillShape),
                ) { Text("Cancel") }
            }
        }
    }
}

/** Every app with a launcher icon, Pathfinder itself aside, by name. */
internal fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launchable, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            AppEntry(
                pkg = it.activityInfo.packageName,
                label = it.loadLabel(pm).toString(),
                icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}
