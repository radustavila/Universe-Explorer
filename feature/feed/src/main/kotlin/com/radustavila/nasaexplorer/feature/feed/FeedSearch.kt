package com.radustavila.nasaexplorer.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.adaptive.CardColumnMinWidth
import com.radustavila.nasaexplorer.core.designsystem.component.ApodCard
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate

const val FEED_SEARCH_FIELD_TEST_TAG = "feed-search-field"
const val FEED_SEARCH_RESULTS_TEST_TAG = "feed-search-results"

/** The feed's app bar while searching: the query field, a way out, and a way to clear it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Opening search is a request to type.
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.feed_search_close),
                )
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.feed_search_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // Results already follow every keystroke; the action only makes room to read them.
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(FEED_SEARCH_FIELD_TEST_TAG),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.feed_search_clear),
                    )
                }
            }
        },
    )
}

/**
 * Search results, always headed by what the search covers: the APOD service cannot search, so only
 * entries already on the device can be found, and the screen must not suggest otherwise.
 */
@Composable
internal fun SearchResults(
    search: FeedSearchState,
    onApodClick: (LocalDate) -> Unit,
    onToggleFavorite: (Apod) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Laid out like the feed: as many columns of cards as the width allows.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CardColumnMinWidth),
        modifier = modifier
            .fillMaxSize()
            .testTag(FEED_SEARCH_RESULTS_TEST_TAG),
        contentPadding = PaddingValues(all = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "scope", span = { GridItemSpan(maxLineSpan) }) { SearchScopeNotice() }

        when {
            search.isBlank -> item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                SearchMessage(stringResource(R.string.feed_search_hint))
            }

            search.hasNoResults -> item(key = "no-results", span = { GridItemSpan(maxLineSpan) }) {
                SearchMessage(stringResource(R.string.feed_search_no_results, search.query.trim()))
            }

            else -> items(items = search.results, key = { apod -> apod.date.toString() }) { apod ->
                ApodCard(
                    apod = apod,
                    onClick = { onApodClick(apod.date) },
                    onToggleFavorite = { onToggleFavorite(apod) },
                )
            }
        }
    }
}

@Composable
private fun SearchScopeNotice() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.feed_search_scope),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    )
}
