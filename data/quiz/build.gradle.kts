plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.radustavila.nasaexplorer.data.quiz"
}

/**
 * The daily quiz and fun facts (F28): the bundled question bank (an asset), the progress store
 * (DataStore, backed up), the daily reminder's WorkManager job, and the question and fact sources
 * made from data other modules cache, read through `:core:domain` contracts only.
 */
dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.work.testing)
}
