plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.compose")
}

android {
    namespace = "com.radustavila.nasaexplorer.core.map"
}

dependencies {
    implementation(libs.maplibre.android.sdk)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)

    testImplementation(projects.core.testing)
}
