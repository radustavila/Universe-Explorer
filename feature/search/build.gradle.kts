plugins {
    id("nasaexplorer.android.feature")
}

android {
    namespace = "com.radustavila.nasaexplorer.feature.search"
}

dependencies {
    // BackHandler, which steps out of one group's full list.
    implementation(libs.androidx.activity.compose)
}
