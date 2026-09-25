plugins {
    id("nasaexplorer.android.feature")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.details"
}

dependencies {
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
}
