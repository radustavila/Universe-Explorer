plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.radustavila.nasaexplorer.core.network"
}

dependencies {
    api(projects.core.common)
    api(projects.core.domain)

    api(libs.retrofit)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.converter.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(projects.core.testing)
}
