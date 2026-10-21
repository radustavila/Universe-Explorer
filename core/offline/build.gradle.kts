plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
}

android {
    namespace = "com.radustavila.nasaexplorer.core.offline"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.network)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    // Serves offline copies through the app's image loader (see OfflineFavoriteImageInterceptor).
    implementation(libs.coil)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.room.testing)
}
