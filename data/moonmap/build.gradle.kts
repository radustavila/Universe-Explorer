plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.radustavila.nasaexplorer.data.moonmap"
}

/**
 * The Moon map (F25): the bundled landing-site table (a Java resource), the subsolar point from
 * `:core:astro`, the on-demand Play asset pack with the zoom 5–6 tiles (measured for Settings), and
 * the landing-site quiz questions.
 */
dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.astro)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.asset.delivery.ktx)

    testImplementation(projects.core.testing)
}
