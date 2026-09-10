plugins {
    id("nasaexplorer.jvm.library")
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(projects.core.domain)

    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
