plugins {
    id("nasaexplorer.android.library")
    id("nasaexplorer.android.hilt")
    id("nasaexplorer.android.room")
}

android {
    namespace = "com.radustavila.nasaexplorer.data.climate"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "CO2_URL", "\"https://gml.noaa.gov/webdata/ccgg/trends/co2/co2_mm_mlo.csv\"")
        buildConfigField(
            "String",
            "GISTEMP_URL",
            "\"https://data.giss.nasa.gov/gistemp/tabledata_v4/GLB.Ts+dSST.csv\"",
        )
        buildConfigField(
            "String",
            "SEA_LEVEL_URL",
            "\"https://www.star.nesdis.noaa.gov/socd/lsa/SeaLevelRise/slr/slr_sla_gbl_free_ref_90.csv\"",
        )
        buildConfigField(
            "String",
            "SEA_ICE_URL",
            "\"https://noaadata.apps.nsidc.org/NOAA/G02135/north/monthly/data/N_09_extent_v4.0.csv\"",
        )
    }
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.network)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
