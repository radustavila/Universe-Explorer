plugins {
    id("nasaexplorer.jvm.library")
}

/**
 * Astronomy on the device, behind the domain's `SkyCalculator` and `SatellitePassPredictor`: Sun,
 * Moon and planets through Astronomy Engine, satellites through an SGP4 port. Pure Kotlin, so the
 * reference tests run on the JVM without Robolectric.
 */
dependencies {
    api(projects.core.domain)
    implementation(libs.astronomy.engine)
    implementation(libs.javax.inject)
}
