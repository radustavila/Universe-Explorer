plugins {
    id("nasaexplorer.android.feature")
    id("nasaexplorer.android.roborazzi")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.launches"
}

dependencies {
    // The pad's position on the detail page (F27).
    implementation(projects.core.map)
}
