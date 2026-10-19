plugins {
    id("nasaexplorer.android.feature")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.earthevents"
}

dependencies {
    implementation(projects.core.map)
}
