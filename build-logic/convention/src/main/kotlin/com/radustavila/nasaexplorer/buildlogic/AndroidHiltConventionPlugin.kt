package com.radustavila.nasaexplorer.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        dependency("implementation", "hilt-android")
        dependency("ksp", "hilt-compiler")
        dependency("testImplementation", "hilt-android-testing")
        dependency("kspTest", "hilt-compiler")
    }
}
