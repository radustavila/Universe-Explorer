plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.compose")
}

android {
    namespace = "com.radustavila.nasaexplorer.core.screenshottesting"
}

// Test-only helpers for the modules applying nasaexplorer.android.roborazzi; never a production dependency.
dependencies {
    implementation(projects.core.designsystem)

    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.robolectric)
    api(libs.androidx.compose.ui.test.junit4)
    implementation(libs.androidx.test.core)
    implementation(libs.coil)
}
