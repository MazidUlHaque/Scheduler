package com.mazidulhaque.kokhon.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.ui.common.EntryRow
import com.mazidulhaque.kokhon.ui.common.KokhonTopBar
import com.mazidulhaque.kokhon.ui.common.kokhonApp
import java.time.Instant

/** Matches a plan against every word of the query, in its title, place, people, notes and original message. */
fun Entry.matches(query: String): Boolean {
    val haystack = listOfNotNull(title, location, notes, source.originalText, *people.toTypedArray()).joinToString(" ").lowercase()
    return query
        .lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .all { haystack.contains(it) }
}

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
) {
    val app = kokhonApp()
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember { app.entryRepository.observeFrom(Instant.EPOCH) }
    val entries by all.collectAsStateWithLifecycle(initialValue = emptyList())
    val now = app.clock.instant()
    // Upcoming first (soonest first), then past (most recent first).
    val results =
        remember(entries, query) {
            if (query.isBlank()) {
                emptyList()
            } else {
                val found = entries.filter { it.matches(query) }
                found.filter { !it.start.isBefore(now) }.sortedBy { it.start } +
                    found.filter { it.start.isBefore(now) }.sortedByDescending { it.start }
            }
        }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
    ) {
        KokhonTopBar(title = stringResource(R.string.search), onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).focusRequester(focus),
        )
        if (query.isNotBlank() && results.isEmpty()) {
            Text(
                stringResource(R.string.no_results, query),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(22.dp),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 22.dp, vertical = 8.dp)) {
            items(results, key = { it.id }) { entry ->
                EntryRow(entry, now, showDate = true) { onOpenEntry(entry.id) }
            }
        }
    }
}
