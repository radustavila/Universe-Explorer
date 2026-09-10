plugins {
    `kotlin-dsl`
}

group = "com.radustavila.nasaexplorer.buildlogic"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "nasaexplorer.android.application"
            implementationClass = "com.radustavila.nasaexplorer.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "nasaexplorer.android.library"
            implementationClass = "com.radustavila.nasaexplorer.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "nasaexplorer.android.compose"
            implementationClass = "com.radustavila.nasaexplorer.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "nasaexplorer.android.feature"
            implementationClass = "com.radustavila.nasaexplorer.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "nasaexplorer.android.hilt"
            implementationClass = "com.radustavila.nasaexplorer.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "nasaexplorer.android.room"
            implementationClass = "com.radustavila.nasaexplorer.buildlogic.AndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "nasaexplorer.jvm.library"
            implementationClass = "com.radustavila.nasaexplorer.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
