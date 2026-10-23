package com.radustavila.nasaexplorer.feature.quiz.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.centeringPadding
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.notification.NotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.notification.rememberNotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.domain.quiz.QuizReminderRules
import com.radustavila.nasaexplorer.core.model.QuizBadge
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.feature.quiz.QuizShareText
import com.radustavila.nasaexplorer.feature.quiz.R
import com.radustavila.nasaexplorer.feature.quiz.appName
import com.radustavila.nasaexplorer.feature.quiz.description
import com.radustavila.nasaexplorer.feature.quiz.icon
import com.radustavila.nasaexplorer.feature.quiz.label
import com.radustavila.nasaexplorer.feature.quiz.play.ResultSquares
import com.radustavila.nasaexplorer.feature.quiz.rememberTextSharer
import com.radustavila.nasaexplorer.feature.quiz.title
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

const val QUIZ_START_TEST_TAG = "quiz-start"
const val QUIZ_REMINDER_SWITCH_TEST_TAG = "quiz-reminder"
const val QUIZ_TOPIC_TEST_TAG = "quiz-topic"

@Composable
fun QuizHomeScreen(
    onBackClick: () -> Unit,
    onDailyClick: () -> Unit,
    onTopicClick: (QuizTopic) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuizHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val reminder = rememberNotificationPermissionToggle(
        isEnabled = uiState.progress.reminderEnabled,
        onEnabledChange = viewModel::onReminderChange,
        snackbarHostState = snackbarHostState,
        blockedMessage = stringResource(R.string.quiz_reminder_blocked),
    )
    QuizHomeContent(
        uiState = uiState,
        reminder = reminder,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onDailyClick = onDailyClick,
        onTopicClick = onTopicClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuizHomeContent(
    uiState: QuizHomeUiState,
    reminder: NotificationPermissionToggle,
    onBackClick: () -> Unit,
    onDailyClick: () -> Unit,
    onTopicClick: (QuizTopic) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        // See FeedScreen: insets belong to the top bar and to the app scaffold's navigation bar.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quiz_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.quiz_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!uiState.isLoaded) {
                LoadingState()
            } else {
                QuizHomeList(uiState, reminder, onDailyClick, onTopicClick)
            }
        }
    }
}

@Composable
private fun QuizHomeList(
    uiState: QuizHomeUiState,
    reminder: NotificationPermissionToggle,
    onDailyClick: () -> Unit,
    onTopicClick: (QuizTopic) -> Unit,
) {
    BoxWithConstraints {
        val sidePadding = centeringPadding(containerWidth = maxWidth) + 16.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "daily") { DailyCard(uiState, onDailyClick) }
            item(key = "stats") { Stats(uiState) }
            item(key = "practice") { Practice(uiState.topics, onTopicClick) }
            item(key = "badges") { Badges(uiState.progress.badges) }
            item(key = "reminder") { ReminderRow(reminder) }
        }
    }
}

@Composable
private fun DailyCard(uiState: QuizHomeUiState, onDailyClick: () -> Unit) {
    val result = uiState.todayResult
    val today = uiState.today
    val resources = LocalResources.current
    val context = LocalContext.current
    val share = rememberTextSharer()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.quiz_daily_title), style = MaterialTheme.typography.titleLarge)
            if (result == null || today == null) {
                Text(stringResource(R.string.quiz_daily_waiting), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onDailyClick, modifier = Modifier.testTag(QUIZ_START_TEST_TAG)) {
                    Text(stringResource(R.string.quiz_daily_start))
                }
            } else {
                Text(
                    stringResource(R.string.quiz_daily_done, result.count { it }, result.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ResultSquares(result)
                OutlinedButton(onClick = { share(QuizShareText.daily(resources, context.appName, today, result)) }) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.quiz_share))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Stats(uiState: QuizHomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.quiz_stats_title))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = STATS_PER_ROW,
        ) {
            val progress = uiState.progress
            Stat(uiState.streak, R.string.quiz_stat_streak, Modifier.weight(1f))
            Stat(progress.longestStreak, R.string.quiz_stat_longest_streak, Modifier.weight(1f))
            Stat(progress.quizzesCompleted, R.string.quiz_stat_quizzes, Modifier.weight(1f))
            Stat(progress.totalCorrect, R.string.quiz_stat_correct, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: Int, label: Int, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(12.dp)) {
            val colors = MaterialTheme.colorScheme
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = colors.primary)
            Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Practice(topics: Map<QuizTopic, Int>, onTopicClick: (QuizTopic) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.quiz_practice_title))
        Text(
            stringResource(R.string.quiz_practice_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = TOPICS_PER_ROW,
        ) {
            topics.filterValues { it > 0 }.forEach { (topic, count) ->
                OutlinedCard(
                    onClick = { onTopicClick(topic) },
                    modifier = Modifier.weight(1f).widthIn(min = TOPIC_MIN_WIDTH).testTag(QUIZ_TOPIC_TEST_TAG),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(topic.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(topic.label()), style = MaterialTheme.typography.titleSmall)
                            Text(
                                pluralStringResource(R.plurals.quiz_topic_questions, count, count),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Badges(earned: Set<QuizBadge>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.quiz_badges_title), Modifier.weight(1f))
            Text(
                stringResource(R.string.quiz_badges_count, earned.size, QuizBadge.entries.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = TOPICS_PER_ROW,
        ) {
            QuizBadge.entries.forEach { badge ->
                val isEarned = badge in earned
                OutlinedCard(Modifier.weight(1f).widthIn(min = TOPIC_MIN_WIDTH).semantics(mergeDescendants = true) {}) {
                    Row(
                        Modifier.padding(12.dp).alpha(if (isEarned) 1f else LOCKED_ALPHA),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isEarned) badge.icon else Icons.Outlined.Lock,
                            contentDescription = if (isEarned) null else stringResource(R.string.quiz_badge_locked),
                            tint = MaterialTheme.colorScheme.run { if (isEarned) primary else onSurfaceVariant },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(badge.title()), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(badge.description()),
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

@Composable
private fun ReminderRow(reminder: NotificationPermissionToggle) {
    val units = rememberUnitFormatter()
    val at = LocalDate.now().atTime(LocalTime.of(QuizReminderRules.HOUR, 0)).atZone(ZoneId.systemDefault()).toInstant()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = reminder.isOn, role = Role.Switch, onValueChange = { reminder.onClick() })
                .padding(16.dp)
                .testTag(QUIZ_REMINDER_SWITCH_TEST_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.quiz_reminder_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.quiz_reminder_description, units.time(at)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            // The row is the control; the switch only shows its state.
            Switch(checked = reminder.isOn, onCheckedChange = null)
        }
    }
}

private const val STATS_PER_ROW = 4
private const val TOPICS_PER_ROW = 2
private const val LOCKED_ALPHA = 0.6f
private val TOPIC_MIN_WIDTH = 150.dp
