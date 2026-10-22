plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.compose")
    id("nasaexplorer.android.roborazzi")
}

android {
    namespace = "com.radustavila.nasaexplorer.core.designsystem"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    // Features read the window's size classes through LocalWindowAdaptiveInfo.
    api(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    testImplementation(projects.core.testing)
}
