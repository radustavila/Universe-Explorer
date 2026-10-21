package com.radustavila.nasaexplorer.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        dependency("implementation", "hilt-android")
        dependency("ksp", "hilt-compiler")
        // No unit test uses Hilt; a module whose tests need it adds hilt-android-testing and
        // kspTest/kspAndroidTest itself (as :app does for its instrumented test).
    }
}
