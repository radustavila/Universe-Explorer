plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
}

android {
    namespace = "com.radustavila.nasaexplorer.core.media"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    // The shows' titles, shared with the Podcasts tab.
    implementation(projects.core.designsystem)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    testImplementation(projects.core.testing)
}
