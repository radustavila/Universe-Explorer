plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    id("nasaexplorer.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.radustavila.nasaexplorer.data.forecast"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "MET_NORWAY_BASE_URL", "\"https://api.met.no/\"")
        // MET Norway bans clients without a User-Agent naming the app and a way to reach its maker.
        buildConfigField("String", "MET_NORWAY_CONTACT", "\"https://github.com/radustavila/Universe-Explorer\"")
    }
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.cache)
    implementation(projects.core.astro)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.work.testing)
}
