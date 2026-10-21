plugins {
    id("nasaexplorer.android.library")
    // Glance is written in Compose, so the module needs the Compose compiler.
    id("nasaexplorer.android.compose")
    id("nasaexplorer.android.hilt")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.widget"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.glance.appwidget.testing)
}
