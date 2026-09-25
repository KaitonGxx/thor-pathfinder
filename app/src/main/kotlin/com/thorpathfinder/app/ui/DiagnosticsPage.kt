package com.thorpathfinder.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.thorpathfinder.app.R
import com.thorpathfinder.app.Diagnostics
import com.thorpathfinder.app.SystemState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The report, on screen and one press from the clipboard.
 *
 * It is built off the main thread because gathering it asks Shizuku for a
 * command, and shown as well as copied so nobody has to paste it somewhere
 * to find out what they are about to share.
 */
@Composable
fun DiagnosticsPage(state: SystemState, onBack: () -> Unit) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    val copy = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode

    LaunchedEffect(Unit) {
        report = withContext(Dispatchers.IO) {
            runCatching { Diagnostics.report(context, state) }
                .getOrElse { context.getString(R.string.diag_failed, it.message.orEmpty()) }
        }
    }
    LaunchedEffect(inputMode, report) { runCatching { copy.requestFocus() } }

    PageScaffold(
        stringResource(R.string.page_diagnostics),
        stringResource(R.string.diag_subtitle),
        onBack = onBack,
    ) {
        val text = report
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Thor Pathfinder diagnostics", text.orEmpty()))
                },
                enabled = text != null,
                modifier = Modifier.focusRequester(copy).focusOutline(PillShape),
            ) {
                Text(stringResource(R.string.diag_copy))
            }
        }
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            Text(
                text ?: stringResource(R.string.diag_gathering),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                // Long lines are kept whole so the report pastes as it reads.
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}
