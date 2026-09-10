plugins {
    id("nasaexplorer.jvm.library")
}

dependencies {
    api(projects.core.model)
    testImplementation(projects.core.testing)
    api(projects.core.common)
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
