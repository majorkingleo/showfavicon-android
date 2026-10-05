// Project layout: one application module. The version catalog lives in
// gradle/libs.versions.toml, so versions are declared in exactly one place.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Dependencies are declared in the module build files only.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "showfavicon-android"
include(":app")
