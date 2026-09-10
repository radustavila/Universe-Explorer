package com.radustavila.nasaexplorer.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Enables Compose (compiler plugin, Material 3, tooling, UI test artifacts) for a module that
 * already applies one of the Android convention plugins.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        when (val androidExtension = extensions.getByName("android")) {
            is ApplicationExtension -> androidExtension.buildFeatures { compose = true }
            is LibraryExtension -> androidExtension.buildFeatures { compose = true }
            else -> error("nasaexplorer.android.compose requires an Android application or library module")
        }

        platformDependency("implementation", "androidx-compose-bom")
        platformDependency("testImplementation", "androidx-compose-bom")
        platformDependency("androidTestImplementation", "androidx-compose-bom")

        dependency("implementation", "androidx-compose-ui")
        dependency("implementation", "androidx-compose-ui-graphics")
        dependency("implementation", "androidx-compose-ui-tooling-preview")
        dependency("implementation", "androidx-compose-material3")
        dependency("implementation", "androidx-compose-material-icons-extended")
        dependency("implementation", "androidx-lifecycle-runtime-compose")
        dependency("debugImplementation", "androidx-compose-ui-tooling")
        dependency("debugImplementation", "androidx-compose-ui-test-manifest")
        dependency("testImplementation", "androidx-compose-ui-test-junit4")
        dependency("androidTestImplementation", "androidx-compose-ui-test-junit4")
    }
}
