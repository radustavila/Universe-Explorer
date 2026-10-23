plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    id("nasaexplorer.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.radustavila.nasaexplorer.data.launches"

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        // Launch Library 2 allows 15 anonymous calls an hour on the production host; its dev host
        // serves the same API with stale-ish data and is meant for development and tests.
        debug {
            buildConfigField("String", "LAUNCH_LIBRARY_BASE_URL", "\"https://lldev.thespacedevs.com/\"")
        }
        release {
            buildConfigField("String", "LAUNCH_LIBRARY_BASE_URL", "\"https://ll.thespacedevs.com/\"")
        }
    }
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.work.testing)
}
