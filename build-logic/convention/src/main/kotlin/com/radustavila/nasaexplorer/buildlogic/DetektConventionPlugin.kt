package com.radustavila.nasaexplorer.buildlogic

import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Static analysis for every Kotlin module. Rules come from Detekt's defaults, overridden by the
 * shared `config/detekt/detekt.yml`. Findings that predate Detekt in CI are recorded in each module's
 * `detekt-baseline.xml` (regenerate with `./gradlew detektBaseline`); anything new fails the build.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dev.detekt")

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig.set(true)
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))
        }
    }
}
