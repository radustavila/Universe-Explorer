plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    id("nasaexplorer.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.radustavila.nasaexplorer.data.dsn"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "DSN_NOW_BASE_URL", "\"https://eyes.nasa.gov/dsn/\"")
        buildConfigField("String", "HORIZONS_BASE_URL", "\"https://ssd.jpl.nasa.gov/\"")
    }
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.cache)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
