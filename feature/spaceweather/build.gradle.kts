plugins {
    id("nasaexplorer.android.feature")
    id("nasaexplorer.android.roborazzi")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.spaceweather"
}

dependencies {
    implementation(projects.core.map)
}
