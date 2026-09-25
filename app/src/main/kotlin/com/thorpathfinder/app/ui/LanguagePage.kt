package com.thorpathfinder.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.thorpathfinder.app.Language
import com.thorpathfinder.app.R

/**
 * Pathfinder's language: the Thor's, or one of the languages it ships, each
 * named in its own language so it can be found from any of them. Choosing one
 * restarts Pathfinder's screens in it.
 */
@Composable
internal fun LanguagePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val chosen = remember { Language.chosen(context) }
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }

    PageScaffold(stringResource(R.string.page_language), stringResource(R.string.language_subtitle), onBack = onBack) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val options = listOf<Pair<String?, String>>(null to stringResource(R.string.language_system)) + Language.CHOICES
            options.forEach { (tag, name) ->
                val selected = tag == chosen
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (selected) Modifier.focusRequester(first) else Modifier)
                        .focusOutline()
                        .clip(RowShape)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            if (!selected) Language.choose(context, tag)
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        if (tag == null) {
                            Text(
                                stringResource(R.string.language_system_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
