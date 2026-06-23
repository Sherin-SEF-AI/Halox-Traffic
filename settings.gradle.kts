pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // MapLibre + some ML artifacts
        maven("https://api.mapbox.com/downloads/v2/releases/maven")
    }
}

rootProject.name = "HaloxTraffic"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:designsystem")
include(":core:model")
include(":core:data")
include(":core:sensors")
include(":core:evidence")
include(":core:export")
include(":core:sync")
include(":feature:detection")
include(":feature:violations")
include(":feature:anpr")
include(":feature:capture")
include(":feature:casefile")
include(":feature:map")
include(":feature:reports")
include(":feature:settings")
