rootProject.name = "MeT-Music_Mobile"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")
include(":webApp")

// During local development, resolve the published-looking dependency below
// from the sibling source repository. CI and packaged builds can remove this
// block to resolve top.met6:applemusic-like-lyrics:0.1.0 from a Maven repo.
includeBuild("../applemusic-like-lyrics_kotlin") {
    dependencySubstitution {
        substitute(module("top.met6:applemusic-like-lyrics")).using(project(":lyrics"))
    }
}
