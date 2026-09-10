plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.compose")
}

android {
    namespace = "com.radustavila.nasaexplorer.core.designsystem"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
