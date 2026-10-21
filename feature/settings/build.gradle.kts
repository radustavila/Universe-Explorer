plugins {
    id("nasaexplorer.android.feature")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.settings"
}

dependencies {
    // Open-source licences: the list itself is generated into :app by the AboutLibraries plugin.
    implementation(libs.aboutlibraries.compose.m3)
}
