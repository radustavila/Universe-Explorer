package com.radustavila.nasaexplorer.feature.mars

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.radustavila.nasaexplorer.feature.mars.photo.MarsPhotoScreen
import com.radustavila.nasaexplorer.feature.mars.photos.MarsPhotosScreen
import kotlinx.serialization.Serializable

/** Every cached photo of one rover, grouped by sol and camera. */
@Serializable
data object MarsPhotosRoute

/** One rover photo; its raw-images id is unique across rovers. */
@Serializable
data class MarsPhotoRoute(val id: String)

fun NavController.navigateToMarsPhotos(navOptions: NavOptions? = null) = navigate(MarsPhotosRoute, navOptions)

fun NavController.navigateToMarsPhoto(id: String, navOptions: NavOptions? = null) =
    navigate(MarsPhotoRoute(id), navOptions)

fun NavGraphBuilder.marsPhotosScreen(
    onBackClick: () -> Unit,
    onPhotoClick: (id: String) -> Unit,
) {
    composable<MarsPhotosRoute> {
        MarsPhotosScreen(onBackClick = onBackClick, onPhotoClick = onPhotoClick)
    }
}

fun NavGraphBuilder.marsPhotoScreen(onBackClick: () -> Unit) {
    composable<MarsPhotoRoute> {
        MarsPhotoScreen(onBackClick = onBackClick)
    }
}
