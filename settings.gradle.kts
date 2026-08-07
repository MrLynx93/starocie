pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "starocie"

include(":shared")

// One module per build, prod and test. They share androidHost/ — the manifest,
// the activity and every resource but the launcher icon — and differ only in
// applicationId, workspace and mark.
include(":androidApp")
include(":androidAppTest")
