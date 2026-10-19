package com.radustavila.nasaexplorer.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.model.FavoriteItem

const val FAVORITES_LIST_TEST_TAG = "favorites-list"

@Composable
fun FavoritesScreen(
    onFavoriteClick: (FavoriteItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FavoritesContent(
        uiState = uiState,
        onFavoriteClick = onFavoriteClick,
        onRemoveFavorite = viewModel::onRemoveFavorite,
        onErrorDismissed = viewModel::onErrorDismissed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesContent(
    uiState: FavoritesUiState,
    onFavoriteClick: (FavoriteItem) -> Unit,
    onRemoveFavorite: (FavoriteItem) -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.asText()

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorDismissed()
        }
    }

    Scaffold(
        modifier = modifier,
        // See FeedScreen: insets belong to the top bar and to the app scaffold's navigation bar.
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.favorites_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))

            uiState.isEmpty -> EmptyState(
                title = stringResource(R.string.favorites_empty_title),
                description = stringResource(R.string.favorites_empty_description),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag(FAVORITES_LIST_TEST_TAG),
                contentPadding = PaddingValues(all = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items = uiState.items, key = { item -> "${item.source.key}/${item.itemId}" }) { item ->
                    FavoriteCard(
                        item = item,
                        onClick = { onFavoriteClick(item) },
                        onRemove = { onRemoveFavorite(item) },
                    )
                }
            }
        }
    }
}
