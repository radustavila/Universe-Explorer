package com.radustavila.nasaexplorer.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Every `:feature:*` module looks the same: an Android library with Compose, Hilt, navigation and
 * access to the domain layer plus the design system. Codifying that here keeps feature modules to a
 * handful of lines and prevents a feature from accidentally reaching into the data layer.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("nasaexplorer.android.library")
        pluginManager.apply("nasaexplorer.android.compose")
        pluginManager.apply("nasaexplorer.android.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        dependencies.add("implementation", project(":core:model"))
        dependencies.add("implementation", project(":core:common"))
        dependencies.add("implementation", project(":core:domain"))
        dependencies.add("implementation", project(":core:designsystem"))
        dependencies.add("testImplementation", project(":core:testing"))

        dependency("implementation", "androidx-lifecycle-viewmodel-compose")
        dependency("implementation", "androidx-navigation-compose")
        dependency("implementation", "androidx-hilt-navigation-compose")
        dependency("implementation", "kotlinx-serialization-json")
        dependency("implementation", "androidx-core-ktx")
    }
}
