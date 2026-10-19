plugins {
    id("nasaexplorer.android.feature")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.spaceweather"
}

dependencies {
    implementation(projects.core.map)
}
