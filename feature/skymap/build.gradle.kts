plugins {
    id("nasaexplorer.android.feature")
    id("nasaexplorer.android.roborazzi")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.skymap"
}

dependencies {
    implementation(projects.core.skymap)
    // AR (F22): the camera preview behind the map. On the device only: nothing is recorded or sent.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
}
