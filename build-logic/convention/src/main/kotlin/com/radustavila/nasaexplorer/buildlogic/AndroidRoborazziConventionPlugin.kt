package com.radustavila.nasaexplorer.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/**
 * Screenshot tests with Roborazzi on Robolectric, for a module that already applies one of the
 * Android convention plugins and Compose.
 *
 * Tests capture through `:core:screenshot-testing`, which renders in the app theme with fake images
 * and writes the golden images to the module's `src/test/screenshots/`. A plain `testDebugUnitTest`
 * only renders them; `recordRoborazziDebug` rewrites the goldens and `verifyRoborazziDebug` fails on
 * any difference (diffs land in `build/outputs/roborazzi/`).
 */
class AndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")

        dependencies.add("testImplementation", project(":core:screenshot-testing"))

        // The goldens are read by the tests, so an edited golden must make verification run again
        // instead of reusing an up-to-date or cached test result.
        val goldens = layout.projectDirectory.dir(GOLDENS_DIR).asFileTree
        tasks.withType<Test>().configureEach {
            inputs.files(goldens).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("roborazziGoldens")
        }
    }
}

private const val GOLDENS_DIR = "src/test/screenshots"
