package com.radustavila.nasaexplorer.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            configureAndroidKotlin(this)

            defaultConfig {
                minSdk = libs.intVersion("minSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            // Robolectric ships Android runtimes up to API 36 while the project compiles against
            // 37; unit tests therefore run against the highest runtime Robolectric can emulate.
            testOptions.targetSdk = libs.intVersion("robolectricSdk")
        }

        configureUnitTestDependencies()
    }
}

internal fun Project.configureUnitTestDependencies() {
    dependency("testImplementation", "junit")
    dependency("testImplementation", "kotlinx-coroutines-test")
    dependency("testImplementation", "turbine")
    dependency("testImplementation", "robolectric")
    dependency("testImplementation", "androidx-test-core")
    dependency("androidTestImplementation", "androidx-test-junit")
    dependency("androidTestImplementation", "androidx-test-runner")
}
