package com.radustavila.nasaexplorer.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

/**
 * The title of a section, card or rail. Marked as a heading, so TalkBack users can jump from one
 * section to the next instead of swiping through every item.
 *
 * Inside a clickable card the heading merges into the card's own node, which then reads as a
 * heading too: that is what lets the hub entries be skimmed the same way.
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier.semantics { heading() },
    )
}
