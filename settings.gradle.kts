pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Astronomy Engine is published only on JitPack; nothing else may resolve from there.
        maven("https://jitpack.io") {
            content { includeGroup("io.github.cosinekitty") }
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "nasa-explorer"

include(":app")
include(":baselineprofile")

include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:notifications")
include(":core:testing")
include(":core:cache")
include(":core:map")
include(":core:wallpaper")
include(":core:astro")
include(":core:location")
include(":core:offline")

include(":data:nasa")
include(":data:launches")
include(":data:nasaimages")
include(":data:mars")
include(":data:earthevents")
include(":data:spaceweather")
include(":data:smallbodies")
include(":data:exoplanets")
include(":data:sky")
include(":data:epic")

include(":feature:feed")
include(":feature:details")
include(":feature:favorites")
include(":feature:debug")
include(":feature:widget")
include(":feature:launches")
include(":feature:universe")
include(":feature:earthmap")
include(":feature:mars")
include(":feature:earthevents")
include(":feature:spaceweather")
include(":feature:exoplanets")
include(":feature:sky")
include(":feature:earth")
