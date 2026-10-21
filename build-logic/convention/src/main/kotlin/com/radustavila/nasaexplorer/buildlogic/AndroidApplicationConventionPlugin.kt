package com.radustavila.nasaexplorer.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("nasaexplorer.detekt")

        extensions.configure<ApplicationExtension> {
            configureAndroidKotlin(this)

            defaultConfig {
                minSdk = libs.intVersion("minSdk")
                targetSdk = libs.intVersion("targetSdk")
                versionCode = 1
                versionName = "1.0.0"
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            // :app's lint also analyses the modules it depends on, so cross-module issues (unused
            // resources, missing translations) are judged against the whole app. Library modules
            // only lint themselves.
            lint.checkDependencies = true

            buildTypes {
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
            }
        }

        configureUnitTestDependencies()
    }
}
