package com.thorpathfinder.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything inside the APK, grouped by license. The texts live in assets/licenses. */
private class Licensed(val name: String, val license: String, val asset: String)

private val LICENSED = listOf(
    Licensed("Thor Pathfinder", "GNU General Public License v3.0", "licenses/GPL-3.0.txt"),
    Licensed("Shizuku API", "MIT License, Copyright (c) 2021 RikkaW", "licenses/Shizuku-API-MIT.txt"),
    Licensed(
        "AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines, JetBrains annotations, Guava ListenableFuture",
        "Apache License 2.0",
        "licenses/Apache-2.0.txt",
    ),
)

/** The open-source licenses of Pathfinder and of the libraries it ships. */
@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    var reading by remember { mutableStateOf<Licensed?>(null) }
    val open = reading
    if (open != null) {
        LicenseText(open, onDismiss = { reading = null })
        return
    }
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    "Open-source licenses",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                LICENSED.forEachIndexed { index, item ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .focusOutline()
                            .clip(RowShape)
                            .clickable { reading = item }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            item.license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp).focusOutline(PillShape),
                ) { Text("Close") }
            }
        }
    }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }
}

/**
 * One license's full text. Each paragraph is a focus stop, so the Thor's
 * D-pad can scroll through it as well as touch.
 */
@Composable
private fun LicenseText(item: Licensed, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var paragraphs by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(item) {
        paragraphs = withContext(Dispatchers.IO) {
            // The files wrap at 80 columns; rejoin each paragraph so it fits the screen.
            context.assets.open(item.asset).bufferedReader().use { it.readText() }
                .split(Regex("""\n\s*\n"""))
                .map { it.trim().replace(Regex("""\s*\n\s*"""), " ") }
                .filter { it.isNotEmpty() }
        }
    }
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    item.license,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                val text = paragraphs
                if (text == null) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).padding(horizontal = 12.dp)) {
                        itemsIndexed(text) { index, paragraph ->
                            Text(
                                paragraph,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                                    .focusOutline()
                                    .focusable()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp).focusOutline(PillShape),
                ) { Text("Back") }
            }
        }
    }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(paragraphs, inputMode) {
        if (!paragraphs.isNullOrEmpty()) runCatching { first.requestFocus() }
    }
}
