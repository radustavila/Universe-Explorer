package com.radustavila.nasaexplorer.feature.comets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.adaptive.ReadableMaxWidth
import com.radustavila.nasaexplorer.core.designsystem.component.EmptyState
import com.radustavila.nasaexplorer.core.designsystem.component.LoadingState
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.CometLocalSky
import com.radustavila.nasaexplorer.core.model.CometTonight
import com.radustavila.nasaexplorer.core.model.CometVisibility
import com.radustavila.nasaexplorer.feature.comets.component.BrightnessCurve
import com.radustavila.nasaexplorer.feature.comets.component.au
import com.radustavila.nasaexplorer.feature.comets.component.bestLine
import com.radustavila.nasaexplorer.feature.comets.component.compassPointRes
import com.radustavila.nasaexplorer.feature.comets.component.magnitude
import com.radustavila.nasaexplorer.feature.comets.component.magnitudeLine
import com.radustavila.nasaexplorer.feature.comets.component.riseSetLine
import kotlin.math.roundToInt

const val COMET_DETAILS_TEST_TAG = "comet-details"

@Composable
fun CometDetailsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CometDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CometDetailsContent(uiState = uiState, onBackClick = onBackClick, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CometDetailsContent(uiState: CometDetailsUiState, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.comet?.comet?.name ?: uiState.designation,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.comets_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val comet = uiState.comet
        when {
            !uiState.isLoaded -> LoadingState(Modifier.padding(padding))
            comet == null -> EmptyState(
                title = stringResource(R.string.comets_details_missing),
                modifier = Modifier.padding(padding),
            )

            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CometDetails(
                    comet = comet,
                    modifier = Modifier
                        .widthIn(max = ReadableMaxWidth)
                        .padding(16.dp)
                        .testTag(COMET_DETAILS_TEST_TAG),
                )
            }
        }
    }
}

@Composable
private fun CometDetails(comet: CometTonight, modifier: Modifier = Modifier) {
    val units = rememberUnitFormatter()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailsSection(stringResource(R.string.comets_details_now_title)) {
            BodyLine(magnitudeLine(comet, units), MaterialTheme.typography.bodyLarge)
            comet.sky?.let { BodyLine(nowLine(it)) }
            BodyLine(stringResource(R.string.comets_constellation, comet.constellation))
            SecondaryLine(
                stringResource(
                    R.string.comets_details_distances,
                    units.au(comet.sunDistanceAu),
                    units.au(comet.earthDistanceAu),
                ),
            )
        }
        comet.sky?.let { sky ->
            DetailsSection(stringResource(R.string.comets_details_tonight_title)) {
                BodyLine(bestLine(sky, units))
                SecondaryLine(riseSetLine(sky, units))
            }
        }
        DetailsSection(stringResource(R.string.comets_details_brightness_title)) {
            BrightnessCurve(points = comet.comet.ephemeris, now = comet.computedAt)
            val peakMagnitude = comet.peakMagnitude
            val peakTime = comet.peakTime
            if (peakMagnitude != null && peakTime != null) {
                BodyLine(stringResource(R.string.comets_peak, units.day(peakTime), units.magnitude(peakMagnitude)))
            }
            SecondaryLine(limitsLine(units))
            SecondaryLine(stringResource(R.string.comets_disclaimer))
        }
        DetailsSection(stringResource(R.string.comets_details_orbit_title)) {
            comet.comet.perihelionTime?.let { perihelion ->
                BodyLine(
                    stringResource(
                        R.string.comets_details_perihelion,
                        units.day(perihelion),
                        units.au(comet.comet.perihelionDistanceAu),
                    ),
                )
            }
            SecondaryLine(stringResource(R.string.comets_credit))
        }
    }
}

@Composable
private fun nowLine(sky: CometLocalSky): String {
    val direction = stringResource(compassPointRes(sky.now.azimuthDegrees))
    return if (sky.now.altitudeDegrees > 0) {
        stringResource(R.string.comets_details_now_position, sky.now.altitudeDegrees.roundToInt(), direction)
    } else {
        stringResource(R.string.comets_details_now_below, direction)
    }
}

@Composable
private fun limitsLine(units: UnitFormatter): String = stringResource(
    R.string.comets_details_limits,
    units.magnitude(CometVisibility.NAKED_EYE_LIMIT),
    units.magnitude(CometVisibility.BINOCULARS_LIMIT),
    units.magnitude(CometVisibility.TELESCOPE_LIMIT),
)

@Composable
private fun DetailsSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle(title)
            content()
        }
    }
}

@Composable
private fun BodyLine(text: String, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(text, style = style)
}

@Composable
private fun SecondaryLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
