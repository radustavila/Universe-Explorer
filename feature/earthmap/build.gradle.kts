plugins {
    id("nasaexplorer.android.feature")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.earthmap"
}

dependencies {
    implementation(projects.core.map)
}
