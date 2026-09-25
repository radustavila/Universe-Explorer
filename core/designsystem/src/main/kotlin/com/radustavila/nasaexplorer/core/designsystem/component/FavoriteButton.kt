package com.radustavila.nasaexplorer.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.radustavila.nasaexplorer.core.designsystem.R

const val FAVORITE_BUTTON_TEST_TAG = "favorite-button"

@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Overridable because the button also sits on top of photography, not only on a surface. */
    unfavoritedTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(onClick = onToggle, modifier = modifier.testTag(FAVORITE_BUTTON_TEST_TAG)) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavorite) {
                    R.string.content_description_favorite_remove
                } else {
                    R.string.content_description_favorite_add
                },
            ),
            tint = if (isFavorite) MaterialTheme.colorScheme.error else unfavoritedTint,
        )
    }
}
