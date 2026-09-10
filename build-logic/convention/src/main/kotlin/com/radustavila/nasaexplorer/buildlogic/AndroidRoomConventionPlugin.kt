package com.radustavila.nasaexplorer.buildlogic

import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("androidx.room")
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<RoomExtension> {
            // Schemas are checked in so migrations can be reviewed and tested.
            schemaDirectory("$projectDir/schemas")
        }

        dependency("implementation", "androidx-room-runtime")
        dependency("implementation", "androidx-room-ktx")
        dependency("ksp", "androidx-room-compiler")
        dependency("testImplementation", "androidx-room-testing")
    }
}
