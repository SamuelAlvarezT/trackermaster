pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Trackermaster"

include(":app")
include(":core:ui")
include(":core:domain")
include(":core:database")
include(":core:data")
include(":core:widgets")
include(":feature:habits")
include(":feature:mood")
include(":feature:expense")
include(":feature:focus")
include(":feature:journal")
include(":feature:insights")
include(":feature:settings")
include(":feature:backup")
