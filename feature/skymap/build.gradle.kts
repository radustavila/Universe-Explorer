plugins {
    id("nasaexplorer.android.feature")
    id("nasaexplorer.android.roborazzi")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.skymap"
}

dependencies {
    implementation(projects.core.skymap)
}
