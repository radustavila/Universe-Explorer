plugins {
    id("nasaexplorer.android.application")
    id("nasaexplorer.android.compose")
    id("nasaexplorer.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.radustavila.nasaexplorer"

    defaultConfig {
        applicationId = "com.radustavila.nasaexplorer"
        // Instrumentation tests run against HiltTestApplication, not NasaExplorerApplication.
        testInstrumentationRunner = "com.radustavila.nasaexplorer.NasaTestRunner"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.core.notifications)
    implementation(projects.core.cache)
    implementation(projects.core.wallpaper)
    implementation(projects.data.nasa)
    implementation(projects.data.launches)
    implementation(projects.data.nasaimages)
    implementation(projects.data.mars)
    implementation(projects.data.earthevents)
    implementation(projects.data.spaceweather)
    implementation(projects.data.smallbodies)
    implementation(projects.data.exoplanets)
    implementation(projects.core.location)
    implementation(projects.data.sky)
    implementation(projects.data.epic)

    implementation(projects.feature.feed)
    implementation(projects.feature.details)
    implementation(projects.feature.favorites)
    implementation(projects.feature.debug)
    implementation(projects.feature.widget)
    implementation(projects.feature.launches)
    implementation(projects.feature.universe)
    implementation(projects.feature.earthmap)
    implementation(projects.feature.mars)
    implementation(projects.feature.earthevents)
    implementation(projects.feature.spaceweather)
    implementation(projects.feature.exoplanets)
    implementation(projects.feature.sky)
    implementation(projects.feature.earth)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(projects.core.testing)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(projects.core.designsystem)
    androidTestImplementation(projects.core.network)
    androidTestImplementation(projects.core.database)
    androidTestImplementation(projects.core.datastore)
    androidTestImplementation(projects.data.nasa)
    androidTestImplementation(projects.data.launches)
    androidTestImplementation(projects.data.nasaimages)
    androidTestImplementation(projects.data.mars)
    androidTestImplementation(projects.data.earthevents)
    androidTestImplementation(projects.data.spaceweather)
    androidTestImplementation(projects.data.smallbodies)
    androidTestImplementation(projects.data.exoplanets)
    androidTestImplementation(projects.data.sky)
    androidTestImplementation(projects.core.location)
    androidTestImplementation(projects.feature.sky)
    androidTestImplementation(projects.data.epic)
    androidTestImplementation(projects.feature.earth)
    androidTestImplementation(projects.feature.feed)
    androidTestImplementation(projects.feature.details)
    androidTestImplementation(projects.feature.favorites)
    androidTestImplementation(projects.feature.universe)
    androidTestImplementation(projects.feature.mars)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.coil.compose)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
