plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
}

android {
    namespace = "com.radustavila.nasaexplorer.core.wallpaper"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)
    // Loads the picture through the app's image loader, as the widget and notification do.
    implementation(projects.core.designsystem)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
}
