package com.radustavila.nasaexplorer.feature.news.item

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.ReadableMaxWidth
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.FavoriteButton
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.ZoomableImageDialog
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.core.model.NewsKind
import com.radustavila.nasaexplorer.feature.news.KindAndAge
import com.radustavila.nasaexplorer.feature.news.R
import com.radustavila.nasaexplorer.feature.news.icon
import com.radustavila.nasaexplorer.feature.news.rememberPageOpener
import java.time.Instant

const val NEWS_ITEM_CONTENT_TEST_TAG = "news-item-content"
const val NEWS_ITEM_OPEN_TEST_TAG = "news-item-open"

@Composable
fun NewsItemScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewsItemViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NewsItemContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onOpenPage = rememberPageOpener(),
        onToggleFavorite = viewModel::onToggleFavorite,
        onErrorDismissed = viewModel::onErrorDismissed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewsItemContent(
    uiState: NewsItemUiState,
    onBackClick: () -> Unit,
    onOpenPage: (NewsItem) -> Unit,
    onToggleFavorite: () -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = (uiState as? NewsItemUiState.Content)?.error?.asText()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorDismissed()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.news_back),
                        )
                    }
                },
                actions = {
                    if (uiState is NewsItemUiState.Content && uiState.canBeSaved) {
                        FavoriteButton(isFavorite = uiState.isFavorite, onToggle = onToggleFavorite)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (uiState) {
                NewsItemUiState.Loading -> LoadingState()

                NewsItemUiState.Unavailable -> EmptyState(
                    title = stringResource(R.string.news_item_missing_title),
                    description = stringResource(R.string.news_item_missing_description),
                )

                is NewsItemUiState.Content -> ItemDetails(uiState.item, uiState.now, onOpenPage)
            }
        }
    }
}

@Composable
private fun ItemDetails(item: NewsItem, now: Instant, onOpenPage: (NewsItem) -> Unit) {
    var isViewerOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    val fullImage = item.fullImageUrl ?: item.imageUrl

    Column(
        modifier = Modifier
            .widthIn(max = ReadableMaxWidth)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(NEWS_ITEM_CONTENT_TEST_TAG),
    ) {
        if (fullImage != null) {
            Hero(
                item = item,
                url = fullImage,
                // A video's picture is its thumbnail: tapping it plays the video instead.
                onClick = { if (item.kind == NewsKind.VIDEO) onOpenPage(item) else isViewerOpen = true },
                onZoom = { isViewerOpen = true }.takeIf { item.kind != NewsKind.VIDEO },
            )
        }
        ItemText(item = item, now = now, onOpenPage = onOpenPage)
    }

    if (isViewerOpen && fullImage != null) {
        ZoomableImageDialog(
            url = fullImage,
            previewUrl = item.imageUrl,
            contentDescription = item.title,
            onDismiss = { isViewerOpen = false },
            caption = item.imageCredit?.let { stringResource(R.string.news_credit, it) },
        )
    }
}

@Composable
private fun Hero(item: NewsItem, url: String, onClick: () -> Unit, onZoom: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HERO_ASPECT_RATIO)
            .background(Color.Black),
    ) {
        NasaAsyncImage(
            url = url,
            previewUrl = item.imageUrl.takeIf { it != url },
            contentDescription = item.title,
            contentScale = ContentScale.Fit,
            backgroundColor = Color.Black,
            fallbackIcon = item.kind.icon,
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
        )
        if (onZoom != null) {
            FilledIconButton(
                onClick = onZoom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ZoomIn,
                    contentDescription = stringResource(R.string.news_item_view_full_size),
                )
            }
        }
    }
}

@Composable
private fun ItemText(item: NewsItem, now: Instant, onOpenPage: (NewsItem) -> Unit) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KindAndAge(item, now)
        Text(text = item.title, style = MaterialTheme.typography.headlineSmall)
        item.imageCredit?.let { credit ->
            Text(
                text = stringResource(R.string.news_credit, credit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.summary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Button(
            onClick = { onOpenPage(item) },
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag(NEWS_ITEM_OPEN_TEST_TAG),
        ) {
            val isVideo = item.kind == NewsKind.VIDEO
            Icon(
                imageVector = if (isVideo) Icons.Outlined.SmartDisplay else Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing),
            )
            Text(stringResource(if (isVideo) R.string.news_item_watch else R.string.news_item_open_page))
        }
    }
}

private const val HERO_ASPECT_RATIO = 4f / 3f
