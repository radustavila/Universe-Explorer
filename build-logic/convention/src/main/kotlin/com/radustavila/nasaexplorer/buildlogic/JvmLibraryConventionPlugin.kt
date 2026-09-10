package com.radustavila.nasaexplorer.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Pure-Kotlin module. Used for `:core:model` and `:core:domain` so the compiler makes it impossible
 * for the domain layer to depend on the Android SDK.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("java-library")
        // Lets Android Lint analyse the pure-Kotlin modules too, instead of treating them as
        // opaque external dependencies.
        pluginManager.apply("com.android.lint")

        configureKotlinCompiler()

        dependency("testImplementation", "junit")
        dependency("testImplementation", "kotlinx-coroutines-test")
        dependency("testImplementation", "turbine")
    }
}
