plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    id("nasaexplorer.android.room")
}

android {
    namespace = "com.radustavila.nasaexplorer.core.database"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
}
