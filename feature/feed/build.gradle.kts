plugins {
    id("nasaexplorer.android.feature")
    id("nasaexplorer.android.roborazzi")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.feed"
}

dependencies {
    // Requests the notification permission when the user turns new-entry notifications on.
    implementation(libs.androidx.activity.compose)
}
