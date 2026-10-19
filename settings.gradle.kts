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
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "nasa-explorer"

include(":app")

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

include(":data:nasa")
include(":data:launches")

include(":feature:feed")
include(":feature:details")
include(":feature:favorites")
include(":feature:debug")
include(":feature:widget")
include(":feature:launches")
