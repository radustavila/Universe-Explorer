package com.radustavila.nasaexplorer.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Shared Android + Kotlin configuration applied to every Android module, so SDK levels, the JVM
 * target and compiler flags cannot drift between modules.
 */
internal fun Project.configureAndroidKotlin(commonExtension: CommonExtension) {
    with(commonExtension) {
        compileSdk = libs.intVersion("compileSdk")

        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17

        packaging.resources.excludes.addAll(
            listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
            ),
        )

        lint.apply {
            // A lint warning in a portfolio project should fail the build, not scroll past in a log.
            warningsAsErrors = true
            abortOnError = true
            // Dependency freshness is managed deliberately through the version catalog.
            disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
        }

        // Robolectric-backed unit tests need real Android resources; see :core:testing.
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = true
    }

    configureKotlinCompiler()
    configureTestJvm()
}

/**
 * Production bytecode targets Java 17, but the unit-test JVM runs on 21: Robolectric's Android 36
 * runtime refuses to start on anything older.
 */
internal fun Project.configureTestJvm() {
    val toolchains = extensions.getByType(JavaToolchainService::class.java)
    tasks.withType<Test>().configureEach {
        javaLauncher.set(
            toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(TEST_JDK_VERSION)) },
        )
    }
}

private const val TEST_JDK_VERSION = 21

internal fun Project.configureKotlinCompiler() {
    extensions.findByType(JavaPluginExtension::class.java)?.apply {
        toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll("-Xconsistent-data-class-copy-visibility")
        }
    }
}
