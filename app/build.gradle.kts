plugins {
    id("nasaexplorer.android.application")
    id("nasaexplorer.android.compose")
    id("nasaexplorer.android.hilt")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
    // Generates res/raw/aboutlibraries.json (the licences screen) from the release dependencies.
    alias(libs.plugins.aboutlibraries.android)
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

    buildTypes {
        getByName("release") {
            // No release keystore yet: release builds are signed with the debug key so they can be
            // installed and checked (CI builds them too). The baseline profile plugin derives
            // benchmarkRelease (debug-signed, profileable, minified like release) for benchmarks.
            signingConfig = signingConfigs.getByName("debug")
            // MapLibre's native library is about 11 MB per ABI. 32-bit x86 devices are practically
            // gone, so release APKs leave it out; debug builds keep every ABI for any emulator.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
    }
}

// The profile is generated on a device by :baselineprofile and checked in under
// src/release/generated/baselineProfiles; release builds only pick it up.
baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
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
    implementation(projects.feature.settings)

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
    // Installs the baseline profile on sideloaded builds too (Play installs it by itself).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.baselineprofile)

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
