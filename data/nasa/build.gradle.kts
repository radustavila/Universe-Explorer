import java.util.Properties

plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The NASA API key never lives in source control: it is read from `local.properties` (or the
 * `NASA_API_KEY` environment variable for CI) and falls back to NASA's public `DEMO_KEY`.
 */
val nasaApiKey: String = run {
    val local = rootProject.file("local.properties")
    val fromLocalProperties = if (local.exists()) {
        Properties().apply { local.inputStream().use(::load) }.getProperty("NASA_API_KEY")
    } else {
        null
    }
    fromLocalProperties?.takeIf(String::isNotBlank)
        ?: System.getenv("NASA_API_KEY")?.takeIf(String::isNotBlank)
        ?: "DEMO_KEY"
}

android {
    namespace = "com.radustavila.nasaexplorer.data.nasa"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "NASA_API_KEY", "\"$nasaApiKey\"")
        buildConfigField("String", "NASA_BASE_URL", "\"https://api.nasa.gov/\"")
    }
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)

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
