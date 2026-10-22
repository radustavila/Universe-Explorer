plugins {
    id("nasaexplorer.jvm.library")
}

/**
 * The sky map's maths and data, in pure Kotlin: J2000 to horizontal coordinates for an observer and
 * instant, the stereographic projection, label placement, and the bundled star catalogue (HYG,
 * CC BY-SA 4.0) and constellation figures (d3-celestial, BSD), read from this module's Java
 * resources. `tools/build_sky_catalog.py` regenerates those files.
 */
dependencies {
    api(projects.core.domain)

    testImplementation(projects.core.testing)
    // Reference positions for the transform tests.
    testImplementation(libs.astronomy.engine)
}
