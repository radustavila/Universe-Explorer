plugins {
    id("nasaexplorer.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.radustavila.nasaexplorer.baselineprofile"
    targetProjectPath = ":app"

    defaultConfig {
        // The emulators are what this project has; their numbers are only good for comparisons.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }
}

// Runs on the device ANDROID_SERIAL names (several devices are usually attached).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
