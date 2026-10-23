package com.radustavila.nasaexplorer.feature.quiz.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.centeringPadding
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.MessageState
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.util.rememberCustomTabOpener
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.feature.quiz.R
import com.radustavila.nasaexplorer.feature.quiz.QuizTextFormatter
import com.radustavila.nasaexplorer.feature.quiz.label
import com.radustavila.nasaexplorer.feature.quiz.rememberQuizTextFormatter

const val QUIZ_ANSWER_TEST_TAG = "quiz-answer"
const val QUIZ_NEXT_TEST_TAG = "quiz-next"
const val QUIZ_EXPLANATION_TEST_TAG = "quiz-explanation"

@Composable
fun QuizPlayScreen(
    onCloseClick: () -> Unit,
    onPracticeAgain: (QuizTopic) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuizPlayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    QuizPlayContent(
        uiState = uiState,
        onAnswer = viewModel::onAnswer,
        onNext = viewModel::onNext,
        onCloseClick = onCloseClick,
        onPracticeAgain = onPracticeAgain,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuizPlayContent(
    uiState: QuizPlayUiState,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onCloseClick: () -> Unit,
    onPracticeAgain: (QuizTopic) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // See FeedScreen: insets belong to the top bar and to the app scaffold's navigation bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(title(uiState.mode)) },
                navigationIcon = {
                    IconButton(onClick = onCloseClick) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.quiz_close))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val question = uiState.current
            val outcome = uiState.outcome
            when {
                uiState.isLoading -> LoadingState()
                outcome != null -> QuizResult(
                    uiState = uiState,
                    outcome = outcome,
                    onDone = onCloseClick,
                    onPracticeAgain = onPracticeAgain,
                )
                question == null -> MessageState(
                    icon = Icons.Outlined.QuestionMark,
                    title = stringResource(R.string.quiz_empty_title),
                    description = stringResource(R.string.quiz_empty_description),
                )
                else -> QuestionPage(uiState = uiState, question = question, onAnswer = onAnswer, onNext = onNext)
            }
        }
    }
}

@Composable
private fun title(mode: QuizMode?): String = when (mode) {
    is QuizMode.Practice -> stringResource(mode.topic.label())
    is QuizMode.Daily, null -> stringResource(R.string.quiz_daily_heading)
}

@Composable
private fun QuestionPage(
    uiState: QuizPlayUiState,
    question: QuizQuestion,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
) {
    val format = rememberQuizTextFormatter()
    BoxWithConstraints {
        val sidePadding = centeringPadding(containerWidth = maxWidth) + 16.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "progress") { QuestionProgress(uiState) }
            question.imageUrl?.let { url ->
                item(key = "image-${question.id}") {
                    NasaAsyncImage(
                        url = url,
                        contentDescription = stringResource(R.string.quiz_image_description),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(IMAGE_ASPECT_RATIO)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
            item(key = "question-${question.id}") {
                SectionTitle(text = format.format(question.prompt), style = MaterialTheme.typography.titleLarge)
            }
            question.answers.forEachIndexed { index, answer ->
                item(key = "answer-${question.id}-$index") {
                    AnswerButton(
                        text = format.format(answer),
                        state = answerState(uiState, question, index),
                        onClick = { onAnswer(index) },
                    )
                }
            }
            item(key = "explanation-${question.id}") {
                AnimatedVisibility(visible = uiState.isAnswered) {
                    Explanation(
                        question = question,
                        isRight = uiState.selected == question.correctIndex,
                        format = format,
                        isLast = uiState.isLast,
                        onNext = onNext,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionProgress(uiState: QuizPlayUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.quiz_progress, uiState.index + 1, uiState.questions.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = {
                val done = uiState.index + if (uiState.isAnswered) 1 else 0
                done.toFloat() / uiState.questions.size
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class AnswerState { OPEN, RIGHT, WRONG, OTHER }

private fun answerState(uiState: QuizPlayUiState, question: QuizQuestion, index: Int): AnswerState = when {
    !uiState.isAnswered -> AnswerState.OPEN
    index == question.correctIndex -> AnswerState.RIGHT
    index == uiState.selected -> AnswerState.WRONG
    else -> AnswerState.OTHER
}

@Composable
private fun AnswerButton(text: String, state: AnswerState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, border) = when (state) {
        AnswerState.RIGHT -> colors.primaryContainer to colors.primary
        AnswerState.WRONG -> colors.errorContainer to colors.error
        AnswerState.OPEN, AnswerState.OTHER -> Color.Transparent to colors.outlineVariant
    }
    val stateDescription = when (state) {
        AnswerState.RIGHT -> stringResource(R.string.quiz_answer_right_description)
        AnswerState.WRONG -> stringResource(R.string.quiz_answer_wrong_description)
        AnswerState.OPEN, AnswerState.OTHER -> null
    }
    OutlinedCard(
        onClick = onClick,
        enabled = state == AnswerState.OPEN,
        colors = CardDefaults.outlinedCardColors(containerColor = container, disabledContainerColor = container),
        border = BorderStroke(if (state == AnswerState.OPEN) 1.dp else 2.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(QUIZ_ANSWER_TEST_TAG)
            .semantics { if (stateDescription != null) contentDescription = "$text. $stateDescription" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (state == AnswerState.OTHER) colors.onSurfaceVariant else colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            when (state) {
                AnswerState.RIGHT -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.primary)
                AnswerState.WRONG -> Icon(Icons.Outlined.Cancel, contentDescription = null, tint = colors.error)
                AnswerState.OPEN, AnswerState.OTHER -> Unit
            }
        }
    }
}

@Composable
private fun Explanation(
    question: QuizQuestion,
    isRight: Boolean,
    format: QuizTextFormatter,
    isLast: Boolean,
    onNext: () -> Unit,
) {
    val openUrl = rememberCustomTabOpener()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth().testTag(QUIZ_EXPLANATION_TEST_TAG)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(
                    text = stringResource(if (isRight) R.string.quiz_answer_right else R.string.quiz_answer_wrong),
                    color = if (isRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(format.format(question.explanation), style = MaterialTheme.typography.bodyMedium)
                question.sourceUrl?.let { url ->
                    TextButton(onClick = { openUrl(url) }, contentPadding = PaddingValues(0.dp)) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.quiz_source))
                    }
                }
            }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag(QUIZ_NEXT_TEST_TAG)) {
            Text(stringResource(if (isLast) R.string.quiz_see_result else R.string.quiz_next))
        }
    }
}

private const val IMAGE_ASPECT_RATIO = 16f / 10f
