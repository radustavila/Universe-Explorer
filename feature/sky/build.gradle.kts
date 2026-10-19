plugins {
    id("nasaexplorer.android.feature")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.sky"
}

dependencies {
    implementation(projects.core.map)
}
