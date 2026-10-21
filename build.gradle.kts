buildscript {
    dependencies {
        // AGP 9 ships built-in Kotlin support pinned to an older KGP; this raises it
        // to the version declared in the version catalog for every subproject.
        classpath(libs.kotlin.gradlePlugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
}
