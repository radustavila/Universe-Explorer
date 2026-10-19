plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
}

android {
    namespace = "com.radustavila.nasaexplorer.core.location"
}

/**
 * Where the user is: the device's coarse location, an offline city list for those who would rather
 * type a place, and the saved choice (DataStore) behind the domain's `ObserverLocationRepository`.
 */
dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
}
