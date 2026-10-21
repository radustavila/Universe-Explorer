package com.radustavila.nasaexplorer.feature.details

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.FavoriteButton
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineBanner
import com.radustavila.nasaexplorer.core.designsystem.component.OfflineState
import com.radustavila.nasaexplorer.core.designsystem.component.VideoPlaceholder
import com.radustavila.nasaexplorer.core.designsystem.component.ZoomableImageDialog
import com.radustavila.nasaexplorer.core.designsystem.error.asText
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate

const val DETAILS_CONTENT_TEST_TAG = "details-content"

@Composable
fun DetailsScreen(
    onBackClick: () -> Unit,
    onApodClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DetailsContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onApodClick = onApodClick,
        onToggleFavorite = viewModel::onToggleFavorite,
        onRetry = viewModel::onRetry,
        onErrorDismissed = viewModel::onErrorDismissed,
        modifier = modifier,
    )
}

/**
 * Immersive detail view: the media is the subject, so it runs edge to edge under the status bar and
 * the controls float on top of it instead of living in an app bar.
 */
@Composable
internal fun DetailsContent(
    uiState: DetailsUiState,
    onBackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
    onErrorDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    onApodClick: (LocalDate) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = (uiState as? DetailsUiState.Content)?.error?.asText()

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorDismissed()
        }
    }

    Scaffold(
        modifier = modifier,
        // The hero media is drawn behind the status bar, so this screen applies insets itself.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (uiState) {
                DetailsUiState.Loading -> LoadingState()

                is DetailsUiState.Unavailable -> {
                    if (uiState.isOffline) {
                        OfflineState(
                            title = stringResource(R.string.details_missing_title),
                            description = stringResource(R.string.details_missing_description),
                            onRetry = onRetry,
                        )
                    } else {
                        EmptyState(
                            title = uiState.error?.asText()
                                ?: stringResource(R.string.details_missing_title),
                            description = stringResource(R.string.details_missing_description),
                            onRetry = onRetry,
                        )
                    }
                    // Nothing is drawn under the status bar here, so the back affordance that the
                    // hero would normally carry has to be provided separately.
                    ScrimIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.details_back),
                        onClick = onBackClick,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(8.dp),
                    )
                }

                is DetailsUiState.Content -> ApodDetails(
                    apod = uiState.apod,
                    isOffline = uiState.isOffline,
                    onBackClick = onBackClick,
                    onApodClick = onApodClick,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun ApodDetails(
    apod: Apod,
    isOffline: Boolean,
    onBackClick: () -> Unit,
    onApodClick: (LocalDate) -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val shareLabel = stringResource(R.string.details_share)
    val streamUrl = apod.streamUrl

    // A still image can be opened at full resolution; a player already shows its media at its
    // own size.
    val fullImageUrl = apod.fullImageUrl?.takeIf { streamUrl == null }
    var isViewerOpen by rememberSaveable(apod.date) { mutableStateOf(false) }

    // The action bar floats over the scrolling content, so the content has to reserve exactly as
    // much room as the bar actually takes - it grows with the font scale and the navigation bar.
    var actionBarHeight by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag(DETAILS_CONTENT_TEST_TAG),
        ) {
            Hero(
                apod = apod,
                onViewFullSize = fullImageUrl?.let { { isViewerOpen = true } },
            )

            if (isOffline) OfflineBanner()

            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.details_title).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = apod.date.formatMedium(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(text = apod.title, style = MaterialTheme.typography.headlineMedium)

                // NASA packs every credit - photographer, text author, institutions - into one
                // field, sometimes across several lines; it is rendered verbatim.
                apod.copyright?.let { copyright ->
                    Text(
                        text = stringResource(R.string.details_copyright, copyright),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Embedded players (YouTube, Vimeo) are web pages rather than streams, so those
                // still need an external app; everything streamable is already playing above.
                if (apod.isPlayable && streamUrl == null) {
                    OutlinedButton(
                        onClick = { context.openUrl(apod.url) },
                        shape = ButtonShape,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.details_watch_video),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                Explanation(
                    apod = apod,
                    onApodClick = onApodClick,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(with(density) { actionBarHeight.toDp() }))
        }

        HeroActions(
            isFavorite = apod.isFavorite,
            onBackClick = onBackClick,
            onToggleFavorite = onToggleFavorite,
            onShareClick = { context.shareApod(apod, shareLabel) },
        )

        ActionBar(
            onViewOnNasaClick = { context.openUrl(apod.webPageUrl) },
            onBackClick = onBackClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { size -> actionBarHeight = size.height },
        )
    }

    if (isViewerOpen && fullImageUrl != null) {
        ZoomableImageDialog(
            url = fullImageUrl,
            previewUrl = apod.previewImageUrl,
            contentDescription = apod.imageDescription,
            onDismiss = { isViewerOpen = false },
        )
    }
}

/**
 * The primary actions stay reachable without scrolling through several hundred words of
 * explanation, so they sit in a bar pinned to the bottom of the screen.
 */
@Composable
private fun ActionBar(
    onViewOnNasaClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onViewOnNasaClick,
                shape = ButtonShape,
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.details_view_on_nasa),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            OutlinedButton(
                onClick = onBackClick,
                shape = ButtonShape,
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.details_see_other_days),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Hero(
    apod: Apod,
    onViewFullSize: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        val mediaModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HERO_ASPECT_RATIO)
        val streamUrl = apod.streamUrl

        when {
            // A real media stream plays in place; nothing leaves the app. The still frame stands in
            // while the stream buffers, so the hero is never a black rectangle.
            streamUrl != null -> ApodVideoPlayer(
                streamUrl = streamUrl,
                posterUrl = apod.previewImageUrl,
                modifier = mediaModifier,
            )

            apod.previewImageUrl == null && apod.isPlayable -> VideoPlaceholder(mediaModifier)

            // The hero is a cropped 4:3 frame, so the standard resolution file the feed already
            // cached fills it indistinguishably from the HD one; the multi-megabyte HD file is
            // fetched only when the viewer is actually opened.
            else -> NasaAsyncImage(
                url = apod.previewImageUrl,
                contentDescription = apod.imageDescription,
                contentScale = ContentScale.Crop,
                modifier = if (onViewFullSize == null) {
                    mediaModifier
                } else {
                    mediaModifier.clickable(onClick = onViewFullSize)
                },
            )
        }

        // The floating controls sit on photography of unknown brightness; a scrim keeps them legible.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                    ),
                ),
        )

        if (onViewFullSize != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(SCRIM_COLOR, CircleShape)
                    .clickable(onClick = onViewFullSize)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ZoomIn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.details_view_full_size),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroActions(
    isFavorite: Boolean,
    onBackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScrimIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.details_back),
            onClick = onBackClick,
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(modifier = Modifier.background(SCRIM_COLOR, CircleShape)) {
            FavoriteButton(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite,
                unfavoritedTint = Color.White,
            )
        }

        ScrimIconButton(
            icon = Icons.Outlined.Share,
            contentDescription = stringResource(R.string.details_share),
            onClick = onShareClick,
        )
    }
}

@Composable
private fun ScrimIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = SCRIM_COLOR,
            contentColor = Color.White,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/**
 * The explanation is long-form text, so it starts clamped and expands on demand.
 *
 * NASA's links stay in it: one that points to another APOD entry opens that entry in the app, any
 * other one opens in the browser.
 */
@Composable
private fun Explanation(
    apod: Apod,
    onApodClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable(apod.date) { mutableStateOf(false) }
    var isClamped by remember(apod.date) { mutableStateOf(false) }
    val context = LocalContext.current
    val currentOnApodClick by rememberUpdatedState(onApodClick)
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val explanation = remember(apod.explanation, apod.explanationLinks, linkStyles) {
        apod.linkedExplanation(
            linkStyles = linkStyles,
            onApodLinkClick = { date -> currentOnApodClick(date) },
            onWebLinkClick = context::openUrl,
        )
    }

    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(
                if (apod.isPlayable) R.string.details_about_video else R.string.details_about_image,
            ),
            style = MaterialTheme.typography.titleLarge,
        )

        Text(
            text = explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (isExpanded) Int.MAX_VALUE else COLLAPSED_EXPLANATION_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout -> if (!isExpanded) isClamped = layout.hasVisualOverflow },
        )

        if (isClamped) {
            Row(
                modifier = Modifier
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (isExpanded) R.string.details_read_less else R.string.details_read_more,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

/**
 * The explanation with its links attached. Link ranges address the stored text, so they are only
 * applied when restoring paragraph breaks left that text as it was - true for everything the current
 * feed delivers, whose whitespace is already collapsed.
 */
private fun Apod.linkedExplanation(
    linkStyles: TextLinkStyles,
    onApodLinkClick: (LocalDate) -> Unit,
    onWebLinkClick: (String) -> Unit,
): AnnotatedString {
    val text = explanation.withParagraphBreaks()
    val links = if (text == explanation) explanationLinks else emptyList()
    return buildAnnotatedString {
        append(text)
        links.filter { it.end <= text.length }.forEach { link ->
            val apodDate = link.apodDate
            if (apodDate != null) {
                val annotation = LinkAnnotation.Clickable(tag = link.url, styles = linkStyles) {
                    onApodLinkClick(apodDate)
                }
                addLink(annotation, start = link.start, end = link.end)
            } else {
                val annotation = LinkAnnotation.Url(url = link.url, styles = linkStyles) {
                    onWebLinkClick(link.url)
                }
                addLink(annotation, start = link.start, end = link.end)
            }
        }
    }
}

/**
 * APOD explanations arrive as a single line in which paragraph breaks survive only as runs of
 * spaces; restoring the breaks is what makes several hundred words of text readable.
 */
private fun String.withParagraphBreaks(): String = trim()
    .split(PARAGRAPH_BREAK)
    .filter(String::isNotBlank)
    .joinToString(separator = "\n\n")

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // Nothing on the device can open the link; the screen stays as it is.
    }
}

private fun Context.shareApod(apod: Apod, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, apod.title)
        putExtra(Intent.EXTRA_TEXT, "${apod.title}\n${apod.webPageUrl}")
    }
    try {
        startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        // No share targets installed.
    }
}

private val SCRIM_COLOR = Color.Black.copy(alpha = 0.45f)
private val ButtonShape = RoundedCornerShape(16.dp)
private const val HERO_ASPECT_RATIO = 4f / 3f
private const val COLLAPSED_EXPLANATION_LINES = 10
private val PARAGRAPH_BREAK = Regex(" {2,}")
