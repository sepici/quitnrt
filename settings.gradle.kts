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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "the-last-hit"

include(":core:domain")

// The Android modules need the Android SDK and Google's Maven repository. Where those
// aren't reachable, -PjvmOnly builds just the plain Kotlin modules with a smaller root build.
if (providers.gradleProperty("jvmOnly").isPresent) {
    rootProject.buildFileName = "build-jvm.gradle.kts"
} else {
    include(":app")
}
