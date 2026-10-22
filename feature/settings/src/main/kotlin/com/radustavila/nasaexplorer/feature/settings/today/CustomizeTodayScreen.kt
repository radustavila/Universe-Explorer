package com.radustavila.nasaexplorer.feature.settings.today

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.model.TodayCardId
import com.radustavila.nasaexplorer.feature.settings.R
import kotlin.math.abs

const val CUSTOMIZE_TODAY_RESET_TEST_TAG = "customize-today-reset"

@Composable
internal fun CustomizeTodayScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomizeTodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CustomizeTodayContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onShownChange = viewModel::onShownChange,
        onReorder = viewModel::onReorder,
        onMove = viewModel::onMove,
        onReset = viewModel::onReset,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomizeTodayContent(
    uiState: CustomizeTodayUiState,
    onBackClick: () -> Unit,
    onShownChange: (TodayCardId, Boolean) -> Unit,
    onReorder: (List<TodayCardId>) -> Unit,
    onMove: (TodayCardId, Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CustomizeTodayTopBar(
                canReset = uiState.isLoaded && !uiState.isDefault,
                onBackClick = onBackClick,
                onReset = onReset,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (!uiState.isLoaded) return@Box
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.customize_today_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SectionTitle(R.string.customize_today_shown_title)
                if (uiState.shown.isEmpty()) {
                    Hint(R.string.customize_today_none_shown)
                } else {
                    ShownCards(uiState.shown, onShownChange, onReorder, onMove)
                }
                SectionTitle(R.string.customize_today_hidden_title)
                if (uiState.hidden.isEmpty()) {
                    Hint(R.string.customize_today_all_shown)
                } else {
                    HiddenCards(uiState.hidden, onShownChange)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeTodayTopBar(canReset: Boolean, onBackClick: () -> Unit, onReset: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.customize_today_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back))
            }
        },
        actions = {
            TextButton(
                onClick = onReset,
                enabled = canReset,
                modifier = Modifier.testTag(CUSTOMIZE_TODAY_RESET_TEST_TAG),
            ) { Text(stringResource(R.string.customize_today_reset)) }
        },
    )
}

@Composable
private fun HiddenCards(cards: List<TodayCardId>, onShownChange: (TodayCardId, Boolean) -> Unit) {
    cards.forEach { card ->
        key(card) {
            CardRow(
                card = card,
                shown = false,
                onShownChange = { onShownChange(card, it) },
                leading = { Spacer(Modifier.width(LEADING_WIDTH)) },
                modifier = Modifier.testTag(customizeTodayRowTestTag(card)),
            )
        }
    }
}

/**
 * The shown cards, reordered by dragging their handles: the dragged row follows the finger and
 * trades places with a neighbour once it is past half that neighbour's height. The new order is
 * saved when the finger lifts. Screen readers get "Move up" / "Move down" actions instead.
 */
@Composable
private fun ShownCards(
    cards: List<TodayCardId>,
    onShownChange: (TodayCardId, Boolean) -> Unit,
    onReorder: (List<TodayCardId>) -> Unit,
    onMove: (TodayCardId, Int) -> Unit,
) {
    val order = remember(cards) { cards.toMutableStateList() }
    var dragged by remember { mutableStateOf<TodayCardId?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<TodayCardId, Int>() }
    val moveUp = stringResource(R.string.customize_today_move_up)
    val moveDown = stringResource(R.string.customize_today_move_down)

    fun swapPastNeighbour(card: TodayCardId) {
        val index = order.indexOf(card)
        val neighbour = if (dragOffset > 0) index + 1 else index - 1
        if (neighbour !in order.indices) return
        val neighbourHeight = heights[order[neighbour]] ?: return
        if (abs(dragOffset) > neighbourHeight / 2f) {
            order.add(neighbour, order.removeAt(index))
            dragOffset += if (neighbour > index) -neighbourHeight else neighbourHeight
        }
    }

    fun endDrag() {
        dragged = null
        dragOffset = 0f
        onReorder(order.toList())
    }

    order.forEachIndexed { index, card ->
        key(card) {
            val isDragged = card == dragged
            Surface(
                tonalElevation = if (isDragged) 6.dp else 0.dp,
                shadowElevation = if (isDragged) 6.dp else 0.dp,
                modifier = Modifier
                    .onSizeChanged { heights[card] = it.height }
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                    .testTag(customizeTodayRowTestTag(card))
                    .semantics {
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(moveUp) { onMove(card, -1); true }.takeIf { index > 0 },
                            CustomAccessibilityAction(moveDown) { onMove(card, 1); true }
                                .takeIf { index < order.lastIndex },
                        )
                    },
            ) {
                CardRow(
                    card = card,
                    shown = true,
                    onShownChange = { onShownChange(card, it) },
                    leading = {
                        DragHandle(
                            card = card,
                            onDragStart = {
                                dragged = card
                                dragOffset = 0f
                            },
                            onDrag = { dy ->
                                dragOffset += dy
                                swapPastNeighbour(card)
                            },
                            onDragEnd = ::endDrag,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DragHandle(card: TodayCardId, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    // The gesture outlives recompositions (a saved order brings a new list): always call the latest.
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(LEADING_WIDTH)
            .testTag(customizeTodayHandleTestTag(card))
            .pointerInput(card) {
                detectDragGestures(
                    onDragStart = { currentOnDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        currentOnDrag(amount.y)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                )
            },
    ) {
        Icon(
            Icons.Outlined.DragHandle,
            contentDescription = stringResource(R.string.customize_today_drag),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CardRow(
    card: TodayCardId,
    shown: Boolean,
    onShownChange: (Boolean) -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        // Everything but the drag handle toggles, so grabbing the handle never flips the switch.
        Row(
            modifier = Modifier
                .weight(1f)
                .toggleable(value = shown, role = Role.Switch, onValueChange = onShownChange)
                .padding(end = 16.dp, top = 10.dp, bottom = 10.dp)
                .testTag(customizeTodaySwitchTestTag(card)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = card.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(card.titleRes), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(card.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = shown, onCheckedChange = null)
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Hint(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private val CONTENT_MAX_WIDTH = 640.dp

/** The drag handle's touch target; hidden cards leave the same space so the icons line up. */
private val LEADING_WIDTH = 48.dp
