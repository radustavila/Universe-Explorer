plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
}

android {
    namespace = "com.radustavila.nasaexplorer.core.cache"
}

dependencies {
    implementation(projects.core.common)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.work.testing)
}
