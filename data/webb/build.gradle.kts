plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    id("nasaexplorer.android.room")
}

android {
    namespace = "com.radustavila.nasaexplorer.data.webb"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField(
            "String",
            "JWST_SCHEDULES_URL",
            "\"https://www.stsci.edu/jwst/science-execution/observing-schedules\"",
        )
    }
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
