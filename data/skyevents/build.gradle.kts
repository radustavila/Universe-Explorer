plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
}

android {
    namespace = "com.radustavila.nasaexplorer.data.skyevents"
}

/**
 * The sky events calendar's storage and wake-ups: the user's reminders in DataStore and the one
 * WorkManager job that posts them. The events themselves are computed by `:core:astro`, offline.
 */
dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.astro)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.work.testing)
}
