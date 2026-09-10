package com.radustavila.nasaexplorer.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider

internal fun Project.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    libs.findLibrary(alias).orElseThrow { IllegalStateException("Library '$alias' missing from version catalog") }

internal fun Project.dependency(configuration: String, alias: String) {
    dependencies.add(configuration, lib(alias))
}

internal fun Project.platformDependency(configuration: String, alias: String) {
    dependencies.add(configuration, dependencies.platform(lib(alias)))
}
