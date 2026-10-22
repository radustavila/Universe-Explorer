plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    id("nasaexplorer.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.radustavila.nasaexplorer.data.spaceweather"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "SWPC_BASE_URL", "\"https://services.swpc.noaa.gov/\"")
        // DONKI moved off api.nasa.gov on 2026-09-30; the old route redirects to a news page.
        buildConfigField("String", "DONKI_BASE_URL", "\"https://ccmc.gsfc.nasa.gov/DONKI-API/\"")
        buildConfigField("String", "SDO_BASE_URL", "\"https://sdo.gsfc.nasa.gov/\"")
        buildConfigField("String", "HELIOVIEWER_BASE_URL", "\"https://api.helioviewer.org/\"")
    }
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.cache)

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
