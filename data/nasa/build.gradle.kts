plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.radustavila.nasaexplorer.data.nasa"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "NASA_BASE_URL", "\"https://science.nasa.gov/\"")
    }
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.cache)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
}
