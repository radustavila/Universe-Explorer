plugins {
    id("nasaexplorer.android.feature")
    id("nasaexplorer.android.roborazzi")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.settings"
}

dependencies {
    // Open-source licences: the list itself is generated into :app by the AboutLibraries plugin.
    implementation(libs.aboutlibraries.compose.m3)
}
